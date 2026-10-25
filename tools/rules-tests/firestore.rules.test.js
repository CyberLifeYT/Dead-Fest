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

// ==================================================================== v2.1.2: image reactions & comments

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

// ==================================================================== v2.1.3: Xbox death evidence
//
// The coin-safety acceptance tests. Every moderation test reads the real balance and death count
// from the emulator before and after, rather than trusting a success message. The moderation
// commits below are the same writes the app makes (DeathModeration + FirebaseRepository): the
// evidence update, the player's death count at playerData.games.<sector>.deaths, and the audit entry.

const { writeBatch, FieldPath, Bytes } = require('firebase/firestore');

const EV_OWNER = 'evOwner';   // a survivor running the detector
const EV_MATE = 'evMate';     // a teammate whose death may be linked by an admin
const SECTOR = 'Dying Light 2';
const OTHER_SECTOR = 'Dead Island 2';
let clock = Date.now();
const tick = () => ++clock;

const seedEvidencePlayers = async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `users/${EV_OWNER}`), {
      uid: EV_OWNER, displayName: 'Owner', admin: false, username: 'Owner',
      playerData: { coins: 777, level: 4, xp: 10, games: { [SECTOR]: { deaths: 4 }, [OTHER_SECTOR]: { deaths: 1 } } }
    });
    await setDoc(doc(db, `users/${EV_MATE}`), {
      uid: EV_MATE, displayName: 'Mate', admin: false, username: 'Mate',
      playerData: { coins: 50, level: 1, xp: 0, games: {} }
    });
  });
};

const playerState = async (uid) => {
  let out;
  await env.withSecurityRulesDisabled(async (context) => {
    const d = (await getDoc(doc(context.firestore(), `users/${uid}`))).data().playerData;
    out = { coins: d.coins, games: Object.fromEntries(Object.entries(d.games || {}).map(([k, v]) => [k, v.deaths || 0])) };
  });
  return out;
};

const evidenceState = async (id) => {
  let out;
  await env.withSecurityRulesDisabled(async (context) => {
    out = (await getDoc(doc(context.firestore(), `deathEvidence/${id}`))).data();
  });
  return out;
};

const evidenceDoc = (id, uid, overrides = {}) => {
  const now = tick();
  return {
    eventId: id, gameId: 'dying_light_2', uid, submitterName: uid, playerLabel: '', eventType: 'self',
    detectedAt: now - 5000, confidence: 0.92, status: 'pending',
    localRelativePath: `DyingLight2/2026-10-25_01-32-10_self-death_${id.slice(0, 8)}.png`,
    hasImage: true, thumb: Bytes.fromUint8Array(new Uint8Array([1, 2, 3])), width: 1600, height: 900,
    sizeBytes: 120000, createdAt: now, updatedAt: now, counted: false, countedSector: '',
    subjectUid: uid, reviewedBy: '', reviewerName: '', reviewedAt: 0, rejectionReason: '',
    source: 'xbox-detector', detectorVersion: '1.0.0', ...overrides
  };
};

/** The detector's submission: evidence + full image + "submitted" audit entry in one commit. */
const submit = (db, id, uid, overrides = {}, { audit = true, blob = true } = {}) => {
  const ev = evidenceDoc(id, uid, overrides);
  const batch = writeBatch(db);
  batch.set(doc(db, `deathEvidence/${id}`), ev);
  if (blob) batch.set(doc(db, `deathEvidenceBlobs/${id}`), { data: Bytes.fromUint8Array(new Uint8Array(64)), uid });
  if (audit) {
    batch.set(doc(db, `deathEvidence/${id}/audit/${ev.createdAt}`), {
      action: 'submitted', actor: uid, actorName: uid, at: ev.createdAt, note: 'Detected on Xbox', changes: {}
    });
  }
  return batch.commit();
};

let evCounter = 0;
const newId = () => `ev${Date.now().toString(36)}${(evCounter++).toString().padStart(6, '0')}x`;

const seedSubmitted = async (uid = EV_OWNER, overrides = {}) => {
  const id = newId();
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), `deathEvidence/${id}`), evidenceDoc(id, uid, overrides));
  });
  return id;
};

/**
 * An admin moderation commit, as the app builds it. `counts` are [uid, sector, newValue] writes to
 * the death statistic; `extraUserWrites` lets a test smuggle in something the rules must refuse.
 */
const moderate = (db, id, fields, { counts = [], audit = true, action = 'verified', extraUserWrites = [] } = {}) => {
  const at = tick();
  const batch = writeBatch(db);
  batch.update(doc(db, `deathEvidence/${id}`), {
    reviewedBy: ALICE, reviewerName: 'Alice', reviewedAt: at, updatedAt: at, ...fields
  });
  for (const [uid, sector, value] of counts) {
    batch.update(doc(db, `users/${uid}`), new FieldPath('playerData', 'games', sector, 'deaths'), value);
  }
  for (const [uid, path, value] of extraUserWrites) batch.update(doc(db, `users/${uid}`), path, value);
  if (audit) {
    batch.set(doc(db, `deathEvidence/${id}/audit/${at}`), { action, actor: ALICE, actorName: 'Alice', at, note: '', changes: {} });
  }
  return batch.commit();
};

test('death evidence: a survivor can submit evidence in their own name', async () => {
  await seedEvidencePlayers();
  await assertSucceeds(submit(asBob(), newId(), BOB));
  await assertSucceeds(submit(asBob(), newId(), BOB, { eventType: 'teammate', playerLabel: 'Marco', subjectUid: '' }));
});

test('death evidence: a submission cannot arrive verified, counted, reviewed or in another name', async () => {
  await assertFails(submit(asBob(), newId(), BOB, { status: 'verified' }));
  await assertFails(submit(asBob(), newId(), BOB, { counted: true, countedSector: SECTOR }));
  await assertFails(submit(asBob(), newId(), BOB, { reviewedBy: BOB }));
  await assertFails(submit(asBob(), newId(), EV_OWNER));
  await assertFails(submit(asBob(), newId(), BOB, { subjectUid: EV_MATE }));
});

test('death evidence: bad games, types, paths and sizes are refused', async () => {
  await assertFails(submit(asBob(), newId(), BOB, { gameId: 'halo_infinite' }));
  await assertFails(submit(asBob(), newId(), BOB, { eventType: 'murder' }));
  await assertFails(submit(asBob(), newId(), BOB, { confidence: 1.5 }));
  await assertFails(submit(asBob(), newId(), BOB, { localRelativePath: '../../Windows/win.ini' }));
  await assertFails(submit(asBob(), newId(), BOB, { localRelativePath: 'C:/Users/x.png' }));
  await assertFails(submit(asBob(), newId(), BOB, { localRelativePath: 'DyingLight2/../secret.png' }));
  await assertFails(submit(asBob(), newId(), BOB, { thumb: Bytes.fromUint8Array(new Uint8Array(110 * 1024)) }));
  await assertFails(submit(asBob(), 'short', BOB));
});

test('death evidence: every submission needs its audit entry', async () => {
  await assertFails(submit(asBob(), newId(), BOB, {}, { audit: false }));
});

test('death evidence: shared evidence is readable by survivors, not strangers', async () => {
  const id = newId();
  await assertSucceeds(submit(asBob(), id, BOB));
  await assertSucceeds(getDoc(doc(asCarol(), `deathEvidence/${id}`)));
  await assertSucceeds(getDoc(doc(asCarol(), `deathEvidenceBlobs/${id}`)));
  await assertFails(getDoc(doc(anonymous(), `deathEvidence/${id}`)));
  await assertFails(getDoc(doc(anonymous(), `deathEvidenceBlobs/${id}`)));
});

test('death evidence: a survivor cannot moderate (server-side, not just hidden buttons)', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertFails(moderate(asBob(), id, { status: 'verified', counted: true, countedSector: SECTOR, reviewedBy: BOB },
    { counts: [[EV_OWNER, SECTOR, before.games[SECTOR] + 1]] }));
  await assertFails(updateDoc(doc(asBob(), `deathEvidence/${id}`), { status: 'rejected' }));
  await assertFails(updateDoc(doc(asCarol(), `deathEvidence/${id}`), { status: 'rejected' }));
  assert.deepStrictEqual(await playerState(EV_OWNER), before);
  assert.strictEqual((await evidenceState(id)).status, 'pending');
});

test('death evidence: verifying counts the death once and leaves the balance unchanged', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, before.games[SECTOR] + 1]] }));
  const after = await playerState(EV_OWNER);
  assert.strictEqual(after.coins, before.coins);
  assert.strictEqual(after.games[SECTOR], before.games[SECTOR] + 1);
  const ev = await evidenceState(id);
  assert.strictEqual(ev.status, 'verified');
  assert.strictEqual(ev.counted, true);
});

test('death evidence: repeating a verification cannot count the death twice', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  const once = await playerState(EV_OWNER);
  // A second "verify" that adds another death is refused.
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, once.games[SECTOR] + 1]] }));
  // A plain repeat (no count change) is harmless.
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR }));
  const after = await playerState(EV_OWNER);
  assert.strictEqual(after.games[SECTOR], start.games[SECTOR] + 1);
  assert.strictEqual(after.coins, start.coins);
});

test('death evidence: a verification that does not count the death is refused', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR }));
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: false }));
  assert.deepStrictEqual(await playerState(EV_OWNER), before);
});

test('death evidence: a moderation commit that touches coins fails as a whole', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR }, {
    counts: [[EV_OWNER, SECTOR, before.games[SECTOR] + 1]],
    extraUserWrites: [[EV_OWNER, 'playerData.coins', before.coins - 100]]
  }));
  // Atomic: nothing from the refused commit landed, so status and statistics still agree.
  assert.deepStrictEqual(await playerState(EV_OWNER), before);
  const ev = await evidenceState(id);
  assert.strictEqual(ev.status, 'pending');
  assert.strictEqual(ev.counted, false);
});

test('death evidence: a decision without its audit entry is refused', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertFails(moderate(asAlice(), id, { status: 'rejected', rejectionReason: 'cutscene' }, { audit: false }));
  assert.strictEqual((await evidenceState(id)).status, 'pending');
  assert.deepStrictEqual(await playerState(EV_OWNER), before);
});

test('death evidence: rejecting a pending death leaves balance and deaths unchanged', async () => {
  const id = await seedSubmitted();
  const before = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'rejected', rejectionReason: 'Loading screen, not a death' },
    { action: 'rejected' }));
  assert.deepStrictEqual(await playerState(EV_OWNER), before);
  assert.strictEqual((await evidenceState(id)).status, 'rejected');
});

test('death evidence: rejecting a counted death takes it back off without touching coins', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertSucceeds(moderate(asAlice(), id, { status: 'rejected', counted: false, rejectionReason: 'Duplicate' },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR]]], action: 'rejected' }));
  assert.deepStrictEqual(await playerState(EV_OWNER), start);
});

test('death evidence: rejecting a counted death must also take the count back', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertFails(moderate(asAlice(), id, { status: 'rejected', counted: false, rejectionReason: 'x' }, { action: 'rejected' }));
  await assertFails(moderate(asAlice(), id, { status: 'rejected', counted: false, rejectionReason: 'x' },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] - 1]], action: 'rejected' }));
  assert.strictEqual((await playerState(EV_OWNER)).games[SECTOR], start.games[SECTOR] + 1);
});

test('death evidence: removing a counted death leaves the balance unchanged', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertSucceeds(moderate(asAlice(), id, { status: 'removed', counted: false, rejectionReason: 'Wrong player' },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR]]], action: 'removed' }));
  assert.deepStrictEqual(await playerState(EV_OWNER), start);
  assert.strictEqual((await evidenceState(id)).status, 'removed');
});

test('death evidence: restoring a rejected death counts it exactly once, coins unchanged', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertSucceeds(moderate(asAlice(), id, { status: 'rejected', counted: false, rejectionReason: 'oops' },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR]]], action: 'rejected' }));
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, rejectionReason: '' },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]], action: 'restored' }));
  // Restoring again cannot add a second death.
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: true },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 2]], action: 'restored' }));
  const after = await playerState(EV_OWNER);
  assert.strictEqual(after.games[SECTOR], start.games[SECTOR] + 1);
  assert.strictEqual(after.coins, start.coins);
});

test('death evidence: correcting the sector of a counted death moves it, coins unchanged', async () => {
  const id = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertSucceeds(moderate(asAlice(), id, { countedSector: OTHER_SECTOR, gameId: 'dead_island_2' }, {
    counts: [[EV_OWNER, SECTOR, start.games[SECTOR]], [EV_OWNER, OTHER_SECTOR, start.games[OTHER_SECTOR] + 1]],
    action: 'corrected'
  }));
  const after = await playerState(EV_OWNER);
  assert.strictEqual(after.games[SECTOR], start.games[SECTOR]);
  assert.strictEqual(after.games[OTHER_SECTOR], start.games[OTHER_SECTOR] + 1);
  assert.strictEqual(after.coins, start.coins);
});

test('death evidence: linking a teammate death to a player counts it for them only, no coins move', async () => {
  const id = await seedSubmitted(EV_OWNER, { eventType: 'teammate', playerLabel: 'Mate', subjectUid: '' });
  const owner = await playerState(EV_OWNER);
  const mate = await playerState(EV_MATE);
  await assertFails(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR }));
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: SECTOR, subjectUid: EV_MATE },
    { counts: [[EV_MATE, SECTOR, 1]] }));
  assert.deepStrictEqual(await playerState(EV_OWNER), owner);
  const mateAfter = await playerState(EV_MATE);
  assert.strictEqual(mateAfter.games[SECTOR], 1);
  assert.strictEqual(mateAfter.coins, mate.coins);
});

test('death evidence: a count already at zero stays at zero, never negative', async () => {
  const id = await seedSubmitted(EV_OWNER, { eventType: 'teammate', subjectUid: '' });
  await assertSucceeds(moderate(asAlice(), id, { status: 'verified', counted: true, countedSector: 'Zero Sector', subjectUid: EV_MATE },
    { counts: [[EV_MATE, 'Zero Sector', 1]] }));
  await env.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), `users/${EV_MATE}`), new FieldPath('playerData', 'games', 'Zero Sector', 'deaths'), 0);
  });
  await assertSucceeds(moderate(asAlice(), id, { status: 'rejected', counted: false, rejectionReason: 'x' }, { action: 'rejected' }));
  assert.strictEqual((await playerState(EV_MATE)).games['Zero Sector'], 0);
});

test('death evidence: counted records cannot be deleted; uncounted ones only by an admin or their pending submitter', async () => {
  const counted = await seedSubmitted();
  const start = await playerState(EV_OWNER);
  await assertSucceeds(moderate(asAlice(), counted, { status: 'verified', counted: true, countedSector: SECTOR },
    { counts: [[EV_OWNER, SECTOR, start.games[SECTOR] + 1]] }));
  await assertFails(deleteDoc(doc(asAlice(), `deathEvidence/${counted}`)));

  const rejected = await seedSubmitted(EV_OWNER, { status: 'rejected' });
  await assertFails(deleteDoc(doc(asBob(), `deathEvidence/${rejected}`)));
  await assertSucceeds(deleteDoc(doc(asAlice(), `deathEvidence/${rejected}`)));

  const mine = newId();
  await assertSucceeds(submit(asBob(), mine, BOB));
  await assertFails(deleteDoc(doc(asCarol(), `deathEvidence/${mine}`)));
  await assertSucceeds(deleteDoc(doc(asBob(), `deathEvidence/${mine}`)));
});

test('death evidence: the audit history is append-only and decisions are admin-only', async () => {
  const id = newId();
  await assertSucceeds(submit(asBob(), id, BOB));
  const at = tick();
  await assertFails(setDoc(doc(asBob(), `deathEvidence/${id}/audit/${at}`), { action: 'verified', actor: BOB, at }));
  await assertSucceeds(setDoc(doc(asAlice(), `deathEvidence/${id}/audit/${at}`), { action: 'corrected', actor: ALICE, at, note: 'checked' }));
  await assertFails(updateDoc(doc(asAlice(), `deathEvidence/${id}/audit/${at}`), { note: 'rewritten' }));
  await assertFails(deleteDoc(doc(asAlice(), `deathEvidence/${id}/audit/${at}`)));
  await assertSucceeds(getDocs(collection(asAlice(), `deathEvidence/${id}/audit`)));
  await assertSucceeds(getDocs(collection(asBob(), `deathEvidence/${id}/audit`)));
  await assertFails(getDocs(collection(asCarol(), `deathEvidence/${id}/audit`)));
});

test('death evidence: images cannot be replaced, and only an admin can remove one on its own', async () => {
  const id = newId();
  await assertSucceeds(submit(asBob(), id, BOB));
  await assertFails(setDoc(doc(asBob(), `deathEvidenceBlobs/${id}`), { data: Bytes.fromUint8Array(new Uint8Array(4)), uid: BOB }));
  await assertFails(deleteDoc(doc(asBob(), `deathEvidenceBlobs/${id}`)));
  await assertSucceeds(deleteDoc(doc(asAlice(), `deathEvidenceBlobs/${id}`)));
  await assertFails(setDoc(doc(asCarol(), `deathEvidenceBlobs/${newId()}`), { data: Bytes.fromUint8Array(new Uint8Array(4)), uid: CAROL }));
});
