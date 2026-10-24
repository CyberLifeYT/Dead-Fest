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




// ==================================================================== v2.1.0: profile, alerts, chat

test('a survivor can edit their own profile (avatar, bio, badge colour, achievements)', async () => {
  await assertSucceeds(updateDoc(doc(asBob(), `users/${BOB}`), {
    'playerData.avatar': 'zombie',
    'playerData.bio': 'Still breathing.',
    'playerData.color': '#4CE07F',
    'playerData.achievements': ['first_blood']
  }));
});

test('a survivor cannot edit somebody else\'s bio', async () => {
  await assertFails(updateDoc(doc(asBob(), `users/${ALICE}`), { 'playerData.bio': 'hacked' }));
});

const alertFrom = (uid) => ({
  type: 'cursed', title: 'You were cursed', body: 'by somebody', fromUid: uid,
  fromName: 'Somebody', createdAt: Date.now(), read: false
});

test('a survivor can send another survivor an alert in their own name', async () => {
  await assertSucceeds(addDoc(collection(asBob(), `users/${ALICE}/alerts`), alertFrom(BOB)));
});

test('an alert cannot be forged in somebody else\'s name', async () => {
  await assertFails(addDoc(collection(asBob(), `users/${ALICE}/alerts`), alertFrom(CAROL)));
});

test('an alert cannot arrive already read or with extra fields', async () => {
  await assertFails(addDoc(collection(asBob(), `users/${ALICE}/alerts`), { ...alertFrom(BOB), read: true }));
  await assertFails(addDoc(collection(asBob(), `users/${ALICE}/alerts`), { ...alertFrom(BOB), coins: 999 }));
});

test('only the owner can read, mark and clear their alerts', async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `users/${ALICE}/alerts/a1`), alertFrom(BOB));
  });
  await assertFails(getDocs(collection(asBob(), `users/${ALICE}/alerts`)));
  await assertSucceeds(getDocs(collection(asAlice(), `users/${ALICE}/alerts`)));
  await assertFails(updateDoc(doc(asBob(), `users/${ALICE}/alerts/a1`), { read: true }));
  await assertFails(updateDoc(doc(asAlice(), `users/${ALICE}/alerts/a1`), { title: 'edited' }));
  await assertSucceeds(updateDoc(doc(asAlice(), `users/${ALICE}/alerts/a1`), { read: true }));
  await assertFails(deleteDoc(doc(asBob(), `users/${ALICE}/alerts/a1`)));
  await assertSucceeds(deleteDoc(doc(asAlice(), `users/${ALICE}/alerts/a1`)));
});

const chatFrom = (uid, extra = {}) => ({
  id: 'm', senderUid: uid, senderName: 'Bob', senderAvatar: '', senderColor: '',
  text: 'hello wasteland', timestamp: serverTimestamp(), ...extra
});

test('a survivor can post a chat message in their own name', async () => {
  await assertSucceeds(setDoc(doc(asBob(), 'messages/m1'), chatFrom(BOB)));
});

test('a chat message cannot be posted in somebody else\'s name or be too long', async () => {
  await assertFails(setDoc(doc(asBob(), 'messages/m2'), chatFrom(ALICE)));
  await assertFails(setDoc(doc(asBob(), 'messages/m3'), chatFrom(BOB, { text: 'x'.repeat(501) })));
});

test('a chat photo thumbnail must stay small', async () => {
  await assertFails(setDoc(doc(asBob(), 'messages/m4'), chatFrom(BOB, { thumb: 'x'.repeat(200 * 1024) })));
});

test('the sender or an admin can delete a chat message, nobody else', async () => {
  await assertSucceeds(setDoc(doc(asAlice(), 'messages/m5'), chatFrom(ALICE)));
  await assertFails(deleteDoc(doc(asBob(), 'messages/m5')));
  await assertSucceeds(deleteDoc(doc(asAlice(), 'messages/m5')));
  await assertSucceeds(deleteDoc(doc(asBob(), 'messages/m1')));
});

test('only an admin can start a flash sale', async () => {
  await assertFails(updateDoc(doc(asBob(), 'gameState/global'), { flashSaleActive: true, flashSalePct: 90 }));
  await assertSucceeds(updateDoc(doc(asAlice(), 'gameState/global'), { flashSaleActive: true, flashSalePct: 30, flashSaleEndsAt: 0 }));
});

// ==================================================================== v2.1.1: image reactions & comments

const seedImage = async (id, uploader) => {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `images/${id}`), {
      uploaderUid: uploader, uploaderName: uploader, caption: '', createdAt: Date.now(),
      width: 10, height: 10, sizeBytes: 10, thumb: 'x'
    });
  });
};

test('a survivor can react once, change it, and take it back', async () => {
  await seedImage('r1', ALICE);
  await assertSucceeds(setDoc(doc(asBob(), `images/r1/reactions/${BOB}`), { emoji: '🔥', createdAt: Date.now() }));
  await assertSucceeds(setDoc(doc(asBob(), `images/r1/reactions/${BOB}`), { emoji: '💀', createdAt: Date.now() }));
  await assertSucceeds(deleteDoc(doc(asBob(), `images/r1/reactions/${BOB}`)));
});

test('a reaction cannot be left for somebody else or use an unknown emoji', async () => {
  await seedImage('r2', ALICE);
  await assertFails(setDoc(doc(asBob(), `images/r2/reactions/${CAROL}`), { emoji: '🔥', createdAt: Date.now() }));
  await assertFails(setDoc(doc(asBob(), `images/r2/reactions/${BOB}`), { emoji: '💩', createdAt: Date.now() }));
  await assertFails(setDoc(doc(asBob(), `images/r2/reactions/${BOB}`), { emoji: '🔥', createdAt: Date.now(), coins: 5 }));
});

test('the uploader can clear reactions on their image; other survivors cannot', async () => {
  await seedImage('r3', ALICE);
  await assertSucceeds(setDoc(doc(asBob(), `images/r3/reactions/${BOB}`), { emoji: '👍', createdAt: Date.now() }));
  await assertFails(deleteDoc(doc(asCarol(), `images/r3/reactions/${BOB}`)));
  await assertSucceeds(deleteDoc(doc(asAlice(), `images/r3/reactions/${BOB}`)));
});

const commentBy = (uid, text = 'nice shot') => ({ uid, name: uid, avatar: '', text, createdAt: Date.now() });

test('a survivor can comment in their own name', async () => {
  await seedImage('c1', ALICE);
  await assertSucceeds(setDoc(doc(asBob(), 'images/c1/comments/k1'), commentBy(BOB)));
});

test('a comment cannot be forged, empty or too long', async () => {
  await seedImage('c2', ALICE);
  await assertFails(setDoc(doc(asBob(), 'images/c2/comments/k1'), commentBy(CAROL)));
  await assertFails(setDoc(doc(asBob(), 'images/c2/comments/k2'), commentBy(BOB, '')));
  await assertFails(setDoc(doc(asBob(), 'images/c2/comments/k3'), commentBy(BOB, 'x'.repeat(301))));
});

test('the author, the uploader or an admin can delete a comment; nobody else', async () => {
  await seedImage('c3', CAROL);
  await assertSucceeds(setDoc(doc(asBob(), 'images/c3/comments/k1'), commentBy(BOB)));
  await assertSucceeds(setDoc(doc(asBob(), 'images/c3/comments/k2'), commentBy(BOB)));
  await assertSucceeds(setDoc(doc(asBob(), 'images/c3/comments/k3'), commentBy(BOB)));
  await assertFails(deleteDoc(doc(env.authenticatedContext('dave').firestore(), 'images/c3/comments/k1')));
  await assertSucceeds(deleteDoc(doc(asBob(), 'images/c3/comments/k1')));
  await assertSucceeds(deleteDoc(doc(asCarol(), 'images/c3/comments/k2')));
  await assertSucceeds(deleteDoc(doc(asAlice(), 'images/c3/comments/k3')));
});

test('weekly challenge progress lives on your own profile', async () => {
  await assertSucceeds(updateDoc(doc(asBob(), `users/${BOB}`), {
    'playerData.weekKey': '2026-W43', 'playerData.weeklyStats': { spins: 3 }, 'playerData.weeklyClaimed': []
  }));
  await assertFails(updateDoc(doc(asBob(), `users/${ALICE}`), { 'playerData.weeklyStats': { spins: 99 } }));
});
