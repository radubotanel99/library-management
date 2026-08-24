# Functional Specification — Library Management System

**Version 1.1 · Working copy**

This is the working markdown source of the client-facing specification.
The client-facing PDF is generated from this file — edit here, regenerate there.

Written in plain language, deliberately free of technical detail, so it can be
reviewed by librarians and decision-makers.

---

## 1. Introduction & Purpose

The Library Management System is a web-based application that helps a library run its entire
day-to-day operation — its collection, its members, and the lending of books — from a single,
clear, easy-to-use interface.

It replaces paper registers and scattered spreadsheets with one reliable, searchable system
that is always up to date. Because it runs in an ordinary web browser, staff can use it from
the front-desk computer, from other computers in the building, or — when appropriate —
securely from anywhere.

## 2. Who Uses the System

- **Library staff (librarians).** The primary users. They manage the catalogue and members,
  record loans and returns, review what is overdue, and adjust the library's settings.
- **Members.** The people who borrow books. Each member is registered so their borrowing can be
  tracked, but in this version members are served by staff at the desk rather than signing in
  themselves.

## 3. Key Concepts

| Term | Meaning |
|---|---|
| **Book (copy)** | Every physical book on the shelf is recorded individually with its own unique **book number**. Two copies of the same title are two separate books. |
| **Category** | A subject grouping — Fiction, History, Children — used to organise the collection. |
| **Member** | A person registered with the library who may borrow books. |
| **Loan** | A record of one book borrowed by one member, from checkout to return. |
| **Loan status** | Where a loan stands: **Active**, **Overdue**, or **Returned**. |
| **Book status** | Whether a copy is in the collection: **Active**, or removed as **Lost**, **Damaged**, or **Withdrawn**. |

## 4. Book Catalogue Management

- **Register a book** with its title, author, unique book number, category, publisher, and price.
- **Edit** any book's details at any time.
- **Browse the full catalogue** in a clear, sortable list.
- **Search and filter** by book number, title, author, or category.
- **Remove a book from the collection**, recording **why** — Lost, Damaged, or Withdrawn — with
  an optional note. The book keeps its full history and is never erased.
- **Review removed books** on a dedicated archive screen showing the reason and note for each,
  filterable by reason.
- **Restore a book** that was removed in error or has since been found, returning it to the
  active collection.
- **Unique book numbers** are enforced across the active collection.

### Removing a book

Removal always requires a reason, so the library can later distinguish "we lost twelve books"
from "we retired twelve worn-out books". An optional note records any further detail.

A book that is currently on loan cannot be removed until it is returned.

Removed books do not appear in the catalogue, cannot be lent, and are not counted in the
collection totals on the home screen. They remain visible on the archive screen and in the
borrowing history of any member who borrowed them.

### Restoring a book

A removed book can be restored to the active collection at any time — for a mis-click, or when
a lost book turns up. Restoring is always a deliberate action: editing a removed book's details
never silently returns it to the collection.

Because a removed book's number becomes available for reuse, it is possible that another book
has taken that number in the meantime. When this happens the system says so clearly and asks
for a new number before completing the restore.

## 5. Category Management

- **Create and edit categories**, each with a name and short description.
- **Organise books** by assigning each one to a category.
- **Browse by category** to see how the collection is distributed.
- **Protected removal** — a category that still holds active books cannot be removed.
- **Unique category names** prevent duplicates.

## 6. Member Management

- **Register a member** with name, email, address, and phone number.
- **Edit** member details as they change.
- **Browse, search, and filter** the member list.
- **Archive a member** who is no longer active, preserving their borrowing history.
- **Protected removal** — a member holding books cannot be removed until they are returned.
- **Unique member names** avoid duplicate records.

## 7. Loans & Returns

- **Lend a book** by selecting the member and the copy. The system records the date automatically.
- **Return a book** in one step, closing the loan and stamping the return date.
- **See all current loans** — who has what, and since when.
- **Filter loans by status** — active, overdue, or completed.

Every loan moves through three stages:

| Status | Meaning |
|---|---|
| **Active** | Borrowed and within the allowed lending period. |
| **Overdue** | Kept beyond the allowed period — flagged automatically. |
| **Returned** | Given back; the loan is closed and kept as history. |

**Built-in safeguards**

- A single book copy can be on **only one active loan at a time**.
- A member can hold **no more than the allowed number of books** at once (§9).
- Only books in the active collection can be lent.

## 8. Overdue Management

- **Automatic overdue detection** — each active loan is measured against the allowed lending
  period and marked **Overdue** on its own once that period passes.
- **Automatic correction** — if the library later extends its lending period, loans that are no
  longer late return to **Active** automatically.
- **Overdue at a glance** — every overdue loan in one place for straightforward follow-up.

## 9. System Settings

- **Lending period** — days a book may be kept before it counts as overdue.
- **Maximum books per member** — how many books one member may hold at once.

Changes take effect immediately, and loans affected by a new setting are re-evaluated at once.

## 10. Dashboard & Statistics

The home screen gives an at-a-glance picture the moment staff arrive:

- **Collection size** — physical copies held.
- **Currently on loan** — books out right now.
- **Overdue count** — loans needing attention.
- **Membership** — registered members.
- **Popularity** — the most frequently borrowed books.

Removed books (lost, damaged, withdrawn) are tracked on the archive screen (§4), not the dashboard.

## 11. Search & Filtering

- **Search across the catalogue, members, and loans.**
- **Quick filtering** by book number, name, category, or status.
- **Sortable lists** arranged the way staff prefer.

## 12. Languages

- **Full interface in English and Romanian.**
- **Switch languages** without losing your place or your data.
- The choice is remembered per person, so two staff members can each work in their own language.

## 13. Access, Security & Deployment

The system is built for library staff, and can be set up to match each library's needs.

**Staff sign-in.** Access is protected by a staff sign-in, so only authorised employees can view
members' details and manage the collection.

**Three ways to run the system.** The same system can be deployed whichever way suits a given
library, and a library can move between them later without losing data:

| Option | Description |
|---|---|
| **On a single computer** | Runs on one computer at the library and is used from there — ideal for a small library operating from a single desk. |
| **On an on-site server** | Runs on a server within the library; any device on the library's own network can use it, so several staff can work at once. |
| **Securely online** | Hosted online and reachable from any location through a web browser, protected by staff sign-in. |

**Data privacy.** Each library's information is kept separate and private to that library. Member
details and borrowing history are never shared between libraries.

## 14. Business Rules at a Glance

- Every book copy has a **unique book number** within the active collection.
- A book copy can be on **only one active loan at a time**.
- A member may hold **up to a set number of books** at once (typically 3).
- Loans become **overdue automatically** once the lending period passes (typically 14 days).
- Books removed from the collection **always record a reason** and can be **restored**.
- Books, categories, and members are **archived, never erased**, preserving full history.
- **Names stay unique** among active records, preventing duplicates.

## 15. Future Enhancements

- **Export to Excel & printable reports.**
- **Member self-service** — members sign in to view their own loans.
- **Automated reminders** — email notifications for books due or overdue.
- **Reservations** — place a hold on a book currently on loan.
