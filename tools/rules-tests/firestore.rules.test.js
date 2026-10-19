/**
 * Firestore security rules tests.
 *
 * These are the tests that matter for the admin side of the app. A rule file nobody has run is a
 * rule file that is either broken (the app cannot write) or wide open (anybody can promote
 * themselves). Each case below is a thing the app actually does, or a thing an attacker would
 * try, and the Firestore emulator decides the answer against the real rules file.
 *
 *   npm run test:rules      (from the repository root)
 */

const fs = require('fs');
const path = require('path');
const { test, before, after } = require('node:test');
const assert = require('node:assert');

const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} = require('@firebase/rules-unit-testing');
const {
  doc,
  setDoc,
  getDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  getDocs,
  collection,
  serverTimestamp
} = require('firebase/firestore');

// The Firestore SDK only started exporting its own Blob in v11; the global one is the same
// class and is what the SDK accepts.
const imageBytes = () => new Blob([Buffer.from('0123456789')], { type: 'image/png' });

const PROJECT_ID = 'dead-fest-b4fc2';
const RULES = fs.readFileSync(path.join(__dirname, '..', '..', 'firestore.rules'), 'utf8');

const ALICE = 'alice';   // admin
const BOB = 'bob';       // ordinary survivor
const CAROL = 'carol';   // a second survivor, not in the database

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: RULES }
  });

  // Seed the fixtures with the rules switched off: an admin document has to be planted before
  // anything can be an admin, because the rules forbid creating one.
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `users/${ALICE}`), {
      uid: ALICE, displayName: 'Alice', email: 'alice@test', admin: true, username: 'Alice',
      playerData: { coins: 500, level: 3, xp: 40, games: {} }
    });
    await setDoc(doc(db, `users/${BOB}`), {
      uid: BOB, displayName: 'Bob', email: 'bob@test', admin: false, username: 'Bob',
      playerData: { coins: 100, level: 1, xp: 0, games: {} }
    });
    await setDoc(doc(db, 'gameState/global'), {
      grandTotal: 10,
      latestVersionCode: 13,
      latestVersionName: '2.0.8',
      updateUrl: 'https://example.test/Dead-Fest.apk',
      updateUrlR8: 'https://example.test/Dead-Fest.R8.apk',
      marketEnabled: true,
      forceSpinProtocol: true,
      games: ['Dead Island 2']
    });
    await setDoc(doc(db, 'usernames/alice'), { uid: ALICE, display: 'Alice' });
    await setDoc(doc(db, 'usernames/bob'), { uid: BOB, display: 'Bob' });
    await setDoc(doc(db, 'gameRequests/r1'), {
      name: 'Dead Island 3', requestedBy: CAROL, requestedByName: 'Carol',
      status: 'pending', createdAt: Date.now(), decidedByName: ''
    });
    await setDoc(doc(db, 'scratchPrizes/p1'), { label: 'Dud', weight: 4, value: '0', type: 'none', enabled: true });
    await setDoc(doc(db, 'levelRewards/r1'), { level: 2, rewardId: 'color_toxic', type: 'color', label: 'Toxic', value: '#4CE07F' });
    await setDoc(doc(db, 'archives/a1'), { name: 'Season 1', timestamp: Date.now(), playerSnapshots: [] });
    await setDoc(doc(db, 'votes/v1'), { options: ['A', 'B'], results: { A: 0, B: 0 }, userVotes: {} });
    await setDoc(doc(db, 'eventLog/e1'), { id: 'e1', category: 'DEATH', message: 'seeded', timestamp: Date.now() });
  });
});

after(async () => {
  if (env) await env.cleanup();
});

const asAlice = () => env.authenticatedContext(ALICE).firestore();
const asBob = () => env.authenticatedContext(BOB).firestore();
const asCarol = () => env.authenticatedContext(CAROL).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

// ==================================================================== players

test('a signed-in survivor can read the player list', async () => {
  await assertSucceeds(getDocs(collection(asBob(), 'users')));
});

test('a stranger can read nothing', async () => {
  await assertFails(getDoc(doc(anonymous(), `users/${BOB}`)));
});

test('a survivor can create their own profile', async () => {
  await assertSucceeds(setDoc(doc(asCarol(), `users/${CAROL}`), {
    uid: CAROL, displayName: 'Carol', email: 'carol@test', admin: false, username: 'Carol',
    playerData: { coins: 250, level: 1, xp: 0, games: {} }
  }));
});

test('a survivor cannot create a profile that arrives with admin rights', async () => {
  await assertFails(setDoc(doc(asCarol(), `users/carol2`), {
    uid: 'carol2', displayName: 'Impostor', email: '', admin: true, username: 'Impostor',
    playerData: { coins: 250, level: 1, xp: 0, games: {} }
  }));
});

test('a survivor cannot promote themselves', async () => {
  await assertFails(updateDoc(doc(asBob(), `users/${BOB}`), { admin: true }));
});

test('a survivor cannot change another player\'s admin flag', async () => {
  await assertFails(updateDoc(doc(asBob(), `users/${ALICE}`), { admin: false }));
});

test('an admin can grant and revoke admin rights', async () => {
  await assertSucceeds(updateDoc(doc(asAlice(), `users/${BOB}`), { admin: true }));
  await assertSucceeds(updateDoc(doc(asAlice(), `users/${BOB}`), { admin: false }));
});

test('a survivor can update their own record', async () => {
  await assertSucceeds(updateDoc(doc(asBob(), `users/${BOB}`), {
    'playerData.coins': 250, 'playerData.xp': 25, 'playerData.level': 2
  }));
});

test('the mutual survival reward can pay another player', async () => {
  // The app writes coins and the bankruptcy level onto other people's documents when someone
  // dies. If these are refused, the reward silently stops working.
  await assertSucceeds(updateDoc(doc(asBob(), `users/${ALICE}`), {
    'playerData.coins': 510, 'playerData.bankruptcyLevel': 0
  }));
});

test('a survivor cannot rewrite another player\'s progression', async () => {
  await assertFails(updateDoc(doc(asBob(), `users/${ALICE}`), {
    'playerData.xp': 999999, 'playerData.level': 50
  }));
});

test('a survivor cannot delete a player', async () => {
  await assertFails(deleteDoc(doc(asBob(), `users/${ALICE}`)));
});

test('an admin can delete a player', async () => {
  await assertSucceeds(deleteDoc(doc(asAlice(), `users/${CAROL}`)));
});

// ==================================================================== global state

test('an admin can publish firmware', async () => {
  await assertSucceeds(setDoc(doc(asAlice(), 'gameState/global'), {
    latestVersionCode: 14, latestVersionName: '2.0.9', updateUrl: 'https://example.test/new.apk'
  }, { merge: true }));
});

test('a survivor cannot repoint the update URL every player is told to download', async () => {
  // This is the most valuable rule in the file: the global document carries the APK link.
  await assertFails(setDoc(doc(asBob(), 'gameState/global'), {
    updateUrl: 'https://attacker.test/evil.apk'
  }, { merge: true }));
});

test('a survivor cannot flip a system switch', async () => {
  await assertFails(setDoc(doc(asBob(), 'gameState/global'), {
    forceSpinProtocol: false, marketEnabled: false, lockdownActive: false
  }, { merge: true }));
});

test('a survivor can bump the global death counter by one', async () => {
  await assertSucceeds(setDoc(doc(asBob(), 'gameState/global'), { grandTotal: 11 }, { merge: true }));
});

test('a survivor cannot inflate the death counter', async () => {
  await assertFails(setDoc(doc(asBob(), 'gameState/global'), { grandTotal: 999999 }, { merge: true }));
});

test('a survivor cannot zero the death counter', async () => {
  await assertFails(setDoc(doc(asBob(), 'gameState/global'), { grandTotal: 0 }, { merge: true }));
});

test('a survivor cannot delete the global document', async () => {
  await assertFails(deleteDoc(doc(asBob(), 'gameState/global')));
});

// ==================================================================== handle registry

test('a survivor can claim a handle', async () => {
  await assertSucceeds(setDoc(doc(asBob(), 'usernames/bobsmith'), {
    uid: BOB, display: 'BobSmith'
  }));
});

test('a survivor cannot claim a handle on somebody else\'s behalf', async () => {
  await assertFails(setDoc(doc(asBob(), 'usernames/carolhandle'), {
    uid: CAROL, display: 'CarolHandle'
  }));
});

test('a survivor cannot take a handle that is already claimed', async () => {
  // Taking it would be an update, and updates to the registry are admin-only.
  await assertFails(setDoc(doc(asBob(), 'usernames/alice'), {
    uid: BOB, display: 'Alice'
  }));
});

test('a survivor can release their own handle when they rename', async () => {
  await assertSucceeds(deleteDoc(doc(asBob(), 'usernames/bob')));
});

test('a survivor cannot release somebody else\'s handle', async () => {
  await assertFails(deleteDoc(doc(asBob(), 'usernames/alice')));
});

// ==================================================================== game requests

test('a survivor can ask for a game', async () => {
  await assertSucceeds(addDoc(collection(asBob(), 'gameRequests'), {
    name: 'The Callisto Protocol',
    coverUrl: '',
    requestedBy: BOB,
    requestedByName: 'Bob',
    status: 'pending',
    createdAt: Date.now(),
    decidedByName: ''
  }));
});

test('a survivor cannot file a request in somebody else\'s name', async () => {
  await assertFails(addDoc(collection(asBob(), 'gameRequests'), {
    name: 'Forged Request',
    coverUrl: '',
    requestedBy: CAROL,
    requestedByName: 'Carol',
    status: 'pending',
    createdAt: Date.now(),
    decidedByName: ''
  }));
});

test('a survivor cannot file a request that starts already approved', async () => {
  await assertFails(addDoc(collection(asBob(), 'gameRequests'), {
    name: 'Self Approved',
    coverUrl: '',
    requestedBy: BOB,
    requestedByName: 'Bob',
    status: 'approved',
    createdAt: Date.now(),
    decidedByName: ''
  }));
});

test('a survivor cannot approve a request', async () => {
  await assertFails(updateDoc(doc(asBob(), 'gameRequests/r1'), {
    status: 'approved', decidedByName: 'Bob'
  }));
});

test('a survivor cannot remove a request', async () => {
  await assertFails(deleteDoc(doc(asBob(), 'gameRequests/r1')));
});

test('an admin can approve and remove a request', async () => {
  await assertSucceeds(updateDoc(doc(asAlice(), 'gameRequests/r1'), {
    status: 'approved', decidedByName: 'Alice'
  }));
  await assertSucceeds(deleteDoc(doc(asAlice(), 'gameRequests/r1')));
});

// ==================================================================== admin-only content

test('a survivor can read the shop, wheel, scratch pool, rewards and archives', async () => {
  await assertSucceeds(getDocs(collection(asBob(), 'scratchPrizes')));
  await assertSucceeds(getDocs(collection(asBob(), 'levelRewards')));
  await assertSucceeds(getDocs(collection(asBob(), 'archives')));
  await assertSucceeds(getDoc(doc(asBob(), 'gameState/global')));
});

test('a survivor cannot rewrite the scratch prize pool', async () => {
  await assertFails(setDoc(doc(asBob(), 'scratchPrizes/p1'), { label: 'Everything', weight: 1, value: '99999', type: 'coins', enabled: true }));
});

test('a survivor cannot rewrite the level reward track', async () => {
  await assertFails(setDoc(doc(asBob(), 'levelRewards/r1'), { level: 2, rewardId: 'color_toxic', type: 'color', label: 'Toxic', value: '#4CE07F' }));
});

test('a survivor cannot forge a season archive', async () => {
  await assertFails(setDoc(doc(asBob(), 'archives/a2'), { name: 'Fake Season', timestamp: Date.now(), playerSnapshots: [] }));
});

test('an admin can rewrite them', async () => {
  await assertSucceeds(setDoc(doc(asAlice(), 'scratchPrizes/p1'), { label: 'Dud', weight: 4, value: '0', type: 'none', enabled: true }));
  await assertSucceeds(setDoc(doc(asAlice(), 'archives/a1'), { name: 'Season 1', timestamp: Date.now(), playerSnapshots: [] }));
});

// ==================================================================== votes and the feed

test('a survivor can cast a vote', async () => {
  await assertSucceeds(updateDoc(doc(asBob(), 'votes/v1'), {
    results: { A: 1, B: 0 }, userVotes: { [BOB]: 0 }
  }));
});

test('a survivor cannot rewrite the options of a vote', async () => {
  await assertFails(updateDoc(doc(asBob(), 'votes/v1'), { options: ['A', 'B', 'C'] }));
});

test('a survivor cannot open a vote', async () => {
  await assertFails(setDoc(doc(asBob(), 'votes/v2'), { options: ['A'], results: {}, userVotes: {} }));
});

test('a survivor can add to the event feed', async () => {
  await assertSucceeds(addDoc(collection(asBob(), 'eventLog'), {
    id: String(Date.now()),
    category: 'DEATH',
    message: 'Bob died in Dead Island 2.',
    userUid: BOB,
    targetUid: null,
    timestamp: serverTimestamp()
  }));
});

test('a survivor cannot delete the event feed', async () => {
  await assertFails(deleteDoc(doc(asBob(), 'eventLog/e1')));
});

// ==================================================================== shared images

/*
 * The thumbnail and the full image are Blob fields in the app. The Firestore Node SDK resolves
 * its browser bundle, and that build refuses to write Blob values, so these tests use strings.
 * The rule expression is the same either way - `size()` on a string counts characters, on a Blob
 * it counts bytes - and the size limit is exercised properly by the oversized case below.
 */

test('a survivor can share an image', async () => {
  await assertSucceeds(setDoc(doc(asBob(), 'images/i1'), {
    uploaderUid: BOB,
    uploaderName: 'Bob',
    caption: 'a screenshot',
    createdAt: Date.now(),
    width: 100,
    height: 100,
    sizeBytes: 10,
    thumb: 'thumbnail-bytes'
  }));
  await assertSucceeds(setDoc(doc(asBob(), 'imageBlobs/i1'), {
    uploaderUid: BOB,
    data: 'blob-bytes'
  }));
});

test('an oversized thumbnail is refused', async () => {
  await assertFails(setDoc(doc(asBob(), 'images/i3'), {
    uploaderUid: BOB,
    uploaderName: 'Bob',
    caption: 'too big',
    createdAt: Date.now(),
    width: 100,
    height: 100,
    sizeBytes: 1,
    thumb: 'x'.repeat(200 * 1024)
  }));
});

test('a survivor cannot post an image in somebody else\'s name', async () => {
  await assertFails(setDoc(doc(asBob(), 'images/i2'), {
    uploaderUid: CAROL,
    uploaderName: 'Carol',
    caption: 'not mine',
    createdAt: Date.now(),
    width: 100,
    height: 100,
    sizeBytes: 10,
    thumb: 'thumbnail-bytes'
  }));
});

test('a survivor can delete their own image', async () => {
  await assertSucceeds(deleteDoc(doc(asBob(), 'images/i1')));
  await assertSucceeds(deleteDoc(doc(asBob(), 'imageBlobs/i1')));
});

test('a survivor cannot delete somebody else\'s image', async () => {
  await assertSucceeds(setDoc(doc(asAlice(), 'images/i9'), {
    uploaderUid: ALICE,
    uploaderName: 'Alice',
    caption: 'alice\'s',
    createdAt: Date.now(),
    width: 100,
    height: 100,
    sizeBytes: 10,
    thumb: 'thumbnail-bytes'
  }));
  await assertFails(deleteDoc(doc(asBob(), 'images/i9')));
  await assertSucceeds(deleteDoc(doc(asAlice(), 'images/i9')));
});



