import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, onSnapshot, runTransaction, query, where, documentId, deleteField, setLogLevel } from 'firebase/firestore';

setLogLevel('silent');
let env;
const record = { date: '2026-11-14', mileage: 1234, fuel: 3.2, fuelEfficiency: null, isEstimated: false, lastUpdated: 1794614400000 };
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
const own = uid => doc(db(uid), 'users', uid, 'records', 'one');
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-supercub-access-test',
    firestore: { host: '127.0.0.1', port: 8085, rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8').replace(/^\uFEFF/, '') }
  });
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const admin = context.firestore();
    for (const uid of ['alice', 'bob']) {
      await setDoc(doc(admin, 'allowedUsers', uid), { enabled: true });
      await setDoc(doc(admin, 'users', uid, 'records', 'one'), record);
    }
    await setDoc(doc(admin, 'allowedUsers', 'no-flag'), {});
    await setDoc(doc(admin, 'allowedUsers', 'disabled'), { enabled: false });
    await setDoc(doc(admin, 'allowedUsers', 'wrong-type'), { enabled: 'true' });
  });
});
after(async () => { if (env) await env.cleanup(); });

test('approved owner can get/list/create/update/delete own records without a users parent document', async () => {
  const alice = db('alice');
  await assertSucceeds(getDoc(own('alice')));
  await assertSucceeds(getDocs(collection(alice, 'users/alice/records')));
  const created = doc(alice, 'users/alice/records/new');
  await assertSucceeds(setDoc(created, { ...record, mileage: null, lastUpdated: null }));
  await assertSucceeds(updateDoc(created, { fuel: 4 }));
  await assertSucceeds(deleteDoc(created));
});
for (const uid of ['missing', 'disabled', 'wrong-type', 'no-flag']) {
  test(uid + ' cannot get/list/write own records', async () => {
    await assertFails(getDoc(own(uid)));
    await assertFails(getDocs(collection(db(uid), 'users', uid, 'records')));
    await assertFails(setDoc(own(uid), record));
  });
}
test('unauthenticated access to approval and records is denied', async () => {
  const anonymous = db(null);
  await assertFails(getDoc(doc(anonymous, 'allowedUsers/alice')));
  await assertFails(getDocs(collection(anonymous, 'allowedUsers')));
  await assertFails(getDoc(doc(anonymous, 'users/alice/records/one')));
  await assertFails(getDocs(collection(anonymous, 'users/alice/records')));
  await assertFails(setDoc(doc(anonymous, 'users/alice/records/new'), record));
});
test('approved users cannot share records or list other users', async () => {
  const alice = db('alice');
  const bobRecord = doc(alice, 'users/bob/records/one');
  await assertFails(getDoc(bobRecord));
  await assertFails(getDocs(collection(alice, 'users/bob/records')));
  await assertFails(setDoc(bobRecord, record));
  await assertFails(updateDoc(bobRecord, { fuel: 5 }));
  await assertFails(deleteDoc(bobRecord));
  await assertFails(getDocs(collection(alice, 'users')));
});
test('authenticated users can read only their own approval, including a missing document', async () => {
  assert.equal((await assertSucceeds(getDoc(doc(db('missing'), 'allowedUsers/missing')))).exists(), false);
  await assertSucceeds(getDoc(doc(db('disabled'), 'allowedUsers/disabled')));
  await assertSucceeds(getDoc(doc(db('alice'), 'allowedUsers/alice')));
  await assertFails(getDoc(doc(db('alice'), 'allowedUsers/bob')));
  await assertFails(getDocs(collection(db('alice'), 'allowedUsers')));
  await assertFails(getDocs(query(collection(db('alice'), 'allowedUsers'), where(documentId(), '==', 'alice'))));
});
test('clients cannot create/update/delete approval documents, even their own', async () => {
  await assertFails(setDoc(doc(db('missing'), 'allowedUsers/missing'), { enabled: true }));
  await assertFails(updateDoc(doc(db('alice'), 'allowedUsers/alice'), { enabled: false }));
  await assertFails(deleteDoc(doc(db('alice'), 'allowedUsers/alice')));
  await assertFails(setDoc(doc(db('alice'), 'allowedUsers/bob'), { enabled: true }));
});
test('revocation is visible to own approval listener and immediately denies new record operations', async () => {
  let unsubscribe;
  const revoked = new Promise((resolve, reject) => {
    unsubscribe = onSnapshot(doc(db('alice'), 'allowedUsers/alice'), snapshot => {
      if (snapshot.exists() && snapshot.data().enabled === false) resolve();
    }, reject);
  });
  try {
    await env.withSecurityRulesDisabled(context => updateDoc(doc(context.firestore(), 'allowedUsers/alice'), { enabled: false }));
    await Promise.race([revoked, new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('revocation listener timed out')), 10000); timer.unref(); })]);
    await assertFails(getDoc(own('alice')));
    await assertFails(setDoc(own('alice'), record));
    await assertFails(deleteDoc(own('alice')));
    await assertSucceeds(getDoc(doc(db('alice'), 'allowedUsers/alice')));
  } finally { unsubscribe?.(); }
});
test('deleting approval denies access and console reapproval restores own access only', async () => {
  await env.withSecurityRulesDisabled(context => deleteDoc(doc(context.firestore(), 'allowedUsers/alice')));
  await assertFails(getDoc(own('alice')));
  await env.withSecurityRulesDisabled(context => setDoc(doc(context.firestore(), 'allowedUsers/alice'), { enabled: true }));
  await assertSucceeds(getDoc(own('alice')));
  await assertFails(getDoc(doc(db('alice'), 'users/bob/records/one')));
});
const corruptions = {
  'missing required date': (() => { const { date, ...rest } = record; return rest; })(),
  'owner hijack field': { ...record, ownerId: 'bob' },
  'negative fuel': { ...record, fuel: -1 },
  'infinite fuel': { ...record, fuel: Infinity },
  'invalid mileage type': { ...record, mileage: '1234' },
  'oversized date': { ...record, date: 'x'.repeat(1000) },
  'invalid estimated flag': { ...record, isEstimated: 'true' },
  'invalid timestamp': { ...record, lastUpdated: -1 }
};
for (const [name, data] of Object.entries(corruptions)) {
  test('create and update reject ' + name, async () => {
    await assertFails(setDoc(doc(db('alice'), 'users/alice/records/new'), data));
    await assertFails(setDoc(own('alice'), data));
  });
}
test('approval-dependent rules support the app import transaction size', async () => {
  const alice = db('alice');
  await assertSucceeds(runTransaction(alice, async tx => {
    const refs = Array.from({ length: 200 }, (_, i) => doc(alice, 'users/alice/records/import-' + i));
    for (const ref of refs) await tx.get(ref);
    for (const ref of refs) tx.set(ref, record);
  }));
});

test('partial updates cannot remove required fields or add authority fields', async () => {
  await assertFails(updateDoc(own('alice'), { date: deleteField() }));
  await assertFails(updateDoc(own('alice'), { ownerId: 'bob' }));
  await assertFails(updateDoc(own('alice'), { fuel: -1 }));
});
