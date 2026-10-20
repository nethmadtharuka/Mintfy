// Automated tests for firestore.rules (SRS NFR-04).
// Run from the repo root:
//   firebase emulators:exec --only firestore --project demo-mintfy "npm --prefix firestore-tests test"

import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-mintfy',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

// ---------- Test users ----------
const alice = () =>
  testEnv.authenticatedContext('alice', { email: 'alice@example.com' }).firestore();
const bob = () =>
  testEnv.authenticatedContext('bob', { email: 'bob@example.com' }).firestore();
const guest = () => testEnv.unauthenticatedContext().firestore();

// ---------- Sample documents (Kavindu's data) ----------
const at = (iso) => Timestamp.fromDate(new Date(iso));
const stamps = () => ({ createdAt: serverTimestamp(), updatedAt: serverTimestamp() });

const expense = (overrides = {}) => ({
  amountCents: 85000, // LKR 850.00 coffee
  date: at('2026-10-20T09:30:00+05:30'),
  categoryId: 'default_food_coffee',
  paymentMethodId: 'default_cash',
  note: 'Coffee with team',
  recurringId: null,
  ...stamps(),
  ...overrides,
});

const salary = (overrides = {}) => ({
  sourceId: 'src_salary',
  sourceType: 'SALARY',
  status: 'RECEIVED',
  date: at('2026-10-25T09:00:00+05:30'),
  currency: 'LKR',
  originalAmountCents: 13250000, // LKR 132,500
  exchangeRate: 1,
  lkrAmountCents: 13250000,
  baseAmountCents: 12000000, // LKR 120,000
  bonusAmountCents: 1250000, // LKR 12,500
  ...stamps(),
  ...overrides,
});

const noSalaryParts = { baseAmountCents: null, bonusAmountCents: null };

const category = (overrides = {}) => ({
  name: 'Food & Coffee',
  spendingType: 'DISCRETIONARY',
  icon: 'coffee',
  builtIn: true,
  archived: false,
  ...stamps(),
  ...overrides,
});

const goal = (overrides = {}) => ({
  name: 'MacBook Pro M4',
  targetAmountCents: 49000000, // LKR 490,000
  startingSavedCents: 1120000, // LKR 11,200
  startDate: at('2026-10-20T00:00:00+05:30'),
  deadline: at('2027-10-20T00:00:00+05:30'),
  status: 'ACTIVE',
  ...stamps(),
  ...overrides,
});

const profile = (overrides = {}) => ({
  email: 'alice@example.com',
  baseCurrency: 'LKR',
  displayName: 'Kavindu',
  estimateMinCents: 16000000,
  estimateMaxCents: 21000000,
  ...stamps(),
  ...overrides,
});

// ---------- FR-A3 / NFR-04: ownership ----------
describe('Ownership (FR-A3, NFR-04)', () => {
  test('owner can create and read their own expense', async () => {
    const ref = doc(alice(), 'users/alice/expenses/e1');
    await assertSucceeds(setDoc(ref, expense()));
    await assertSucceeds(getDoc(ref));
  });

  test('another user cannot read it', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice/expenses/e1'), expense()));
    await assertFails(getDoc(doc(bob(), 'users/alice/expenses/e1')));
  });

  test('another user cannot write into it', async () => {
    await assertFails(setDoc(doc(bob(), 'users/alice/expenses/e2'), expense()));
  });

  test('signed-out user cannot read', async () => {
    await assertFails(getDoc(doc(guest(), 'users/alice/expenses/e1')));
  });

  test('unknown collections are denied', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice/secrets/x'), { a: 1 }));
    await assertFails(setDoc(doc(alice(), 'hack/x'), { a: 1 }));
  });
});

// ---------- NFR-05 / FR-H3: money and shape ----------
describe('Expenses: money and shape (NFR-05, FR-H3)', () => {
  test('valid expense is accepted', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice/expenses/e1'), expense()));
  });

  test('decimal amount is rejected', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice/expenses/e1'), expense({ amountCents: 850.5 })));
  });

  test('zero amount is rejected', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice/expenses/e1'), expense({ amountCents: 0 })));
  });

  test('unknown field is rejected', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice/expenses/e1'), expense({ category: 'Food' })));
  });

  test('client-chosen createdAt is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'users/alice/expenses/e1'), expense({ createdAt: Timestamp.now() })),
    );
  });

  test('editing keeps createdAt and refreshes updatedAt (FR-C5)', async () => {
    const ref = doc(alice(), 'users/alice/expenses/e1');
    await assertSucceeds(setDoc(ref, expense()));
    await assertSucceeds(updateDoc(ref, { amountCents: 90000, updatedAt: serverTimestamp() }));
  });

  test('editing cannot change createdAt', async () => {
    const ref = doc(alice(), 'users/alice/expenses/e1');
    await assertSucceeds(setDoc(ref, expense()));
    await assertFails(updateDoc(ref, { createdAt: Timestamp.now(), updatedAt: serverTimestamp() }));
  });

  test('owner can delete an expense (FR-C5)', async () => {
    const ref = doc(alice(), 'users/alice/expenses/e1');
    await assertSucceeds(setDoc(ref, expense()));
    await assertSucceeds(deleteDoc(ref));
  });
});

// ---------- FR-B3 to FR-B9, FR-E6: multi-currency income ----------
describe('Income entries (FR-B3 to FR-B9, FR-E6)', () => {
  const entry = (id) => doc(alice(), `users/alice/incomeEntries/${id}`);

  test('salary with base + bonus is accepted', async () => {
    await assertSucceeds(setDoc(entry('i1'), salary()));
  });

  test('salary where base + bonus does not add up is rejected', async () => {
    await assertFails(setDoc(entry('i1'), salary({ bonusAmountCents: 1000000 })));
  });

  test('AdSense USD receipt keeps amount, rate and LKR value (FR-B5)', async () => {
    await assertSucceeds(
      setDoc(entry('i2'), salary({
        ...noSalaryParts,
        sourceId: 'src_adsense',
        sourceType: 'ADSENSE',
        currency: 'USD',
        originalAmountCents: 5230, // USD 52.30
        exchangeRate: 298.75,
        lkrAmountCents: 1554000, // LKR 15,540 actually credited
      })),
    );
  });

  test('LKR receipt with an exchange rate other than 1 is rejected', async () => {
    await assertFails(setDoc(entry('i3'), salary({ exchangeRate: 2 })));
  });

  test('freelance invoice can be INVOICED (FR-B4)', async () => {
    await assertSucceeds(
      setDoc(entry('i4'), salary({
        ...noSalaryParts,
        sourceId: 'src_freelance',
        sourceType: 'FREELANCE',
        status: 'INVOICED',
        originalAmountCents: 4500000, // LKR 45,000 milestone
        lkrAmountCents: 4500000,
        client: 'Lanka Bites',
        project: 'WordPress site',
        milestone: 'Milestone 2',
        dueDate: at('2026-11-05T00:00:00+05:30'),
      })),
    );
  });

  test('salary cannot be INVOICED (FR-E6)', async () => {
    await assertFails(setDoc(entry('i5'), salary({ status: 'INVOICED' })));
  });

  test('crypto loss is a signed negative amount (FR-B9)', async () => {
    await assertSucceeds(
      setDoc(entry('i6'), salary({
        ...noSalaryParts,
        sourceId: 'src_crypto',
        sourceType: 'CRYPTO',
        currency: 'USDT',
        originalAmountCents: -6000, // -60 USDT
        exchangeRate: 300,
        lkrAmountCents: -1800000, // -LKR 18,000
      })),
    );
  });

  test('negative salary is rejected', async () => {
    await assertFails(
      setDoc(entry('i7'), salary({ ...noSalaryParts, originalAmountCents: -100, lkrAmountCents: -100 })),
    );
  });
});

// ---------- FR-D1, FR-D2: categories ----------
describe('Categories (FR-D1, FR-D2)', () => {
  const ref = () => doc(alice(), 'users/alice/categories/default_food_coffee');

  test('valid default category is accepted', async () => {
    await assertSucceeds(setDoc(ref(), category()));
  });

  test('spending type must be COMMITTED or DISCRETIONARY', async () => {
    await assertFails(setDoc(ref(), category({ spendingType: 'MAYBE' })));
  });

  test('categories cannot be deleted, only archived', async () => {
    await assertSucceeds(setDoc(ref(), category()));
    await assertFails(deleteDoc(ref()));
  });
});

// ---------- FR-E1: goal ----------
describe('Savings goal (FR-E1)', () => {
  test('MacBook goal is accepted', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice/goals/g1'), goal()));
  });

  test('deadline before start date is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'users/alice/goals/g1'), goal({ deadline: at('2026-01-01T00:00:00+05:30') })),
    );
  });
});

// ---------- FR-A1, FR-B8: profile ----------
describe('Profile (FR-A1, FR-B8)', () => {
  test('profile with own email is accepted', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), profile()));
  });

  test('profile with someone else\'s email is rejected', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice'), profile({ email: 'bob@example.com' })));
  });

  test('estimate range min > max is rejected', async () => {
    await assertFails(
      setDoc(doc(alice(), 'users/alice'), profile({ estimateMinCents: 30000000 })),
    );
  });
});
