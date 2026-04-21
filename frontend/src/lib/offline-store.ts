import { openDB } from "idb";

import { sortMembers } from "@/lib/member-utils";
import type { MemberRecord, QueueOperation } from "@/lib/types";

const DB_NAME = "force-gym-next-offline";
const DB_VERSION = 1;
const MEMBERS_STORE = "members";
const QUEUE_STORE = "queue";

let databasePromise: ReturnType<typeof openDB> | null = null;

function getDatabase() {
  if (!databasePromise) {
    databasePromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(database) {
        if (!database.objectStoreNames.contains(MEMBERS_STORE)) {
          database.createObjectStore(MEMBERS_STORE, { keyPath: "id" });
        }

        if (!database.objectStoreNames.contains(QUEUE_STORE)) {
          database.createObjectStore(QUEUE_STORE, { keyPath: "id" });
        }
      }
    });
  }

  return databasePromise;
}

export async function getAllMembers() {
  const database = await getDatabase();
  const members = await database.getAll(MEMBERS_STORE);
  return sortMembers(members as MemberRecord[]);
}

export async function replaceMembers(members: MemberRecord[]) {
  const database = await getDatabase();
  const transaction = database.transaction(MEMBERS_STORE, "readwrite");
  await transaction.store.clear();

  for (const member of members) {
    await transaction.store.put(member);
  }

  await transaction.done;
}

export async function upsertMember(member: MemberRecord) {
  const database = await getDatabase();
  await database.put(MEMBERS_STORE, member);
}

export async function removeMember(memberId: string) {
  const database = await getDatabase();
  await database.delete(MEMBERS_STORE, memberId);
}

export async function enqueueOperation(operation: QueueOperation) {
  const database = await getDatabase();
  await database.put(QUEUE_STORE, operation);
}

export async function getQueuedOperations() {
  const database = await getDatabase();
  const operations = await database.getAll(QUEUE_STORE);
  return (operations as QueueOperation[]).sort((left, right) => left.requestedAt.localeCompare(right.requestedAt));
}

export async function clearQueue() {
  const database = await getDatabase();
  await database.clear(QUEUE_STORE);
}

export async function getQueueCount() {
  const database = await getDatabase();
  return database.count(QUEUE_STORE);
}