# Optional secondary text provider

A small public extension could let a user select a provider of secondary text for favorite items. The launcher would remain the owner of placement, typography, truncation and interactions.

A provider could publish a bounded, versioned snapshot keyed by a public stable item identity, such as an app package and Android profile. Each record would contain optional short text, an expiry time and an optional public PendingIntent. A content URI with change notifications would allow event-driven refresh. Empty or expired results remove the secondary line. The launcher must handle a missing, uninstalled or incompatible provider as no secondary text.

User consent should select the provider and eligible items. Providers should authenticate the caller; the launcher should treat content as sensitive and offer locked-screen redaction. The contract should place strict limits on text length, record count and update rate, and have no dependency on launcher internal types or favorite storage.

This would support notification previews, task status and other brief context. Kvaesitso Bridge could implement the provider in an optional isolated module while retaining its standard widget. This is a proposal, not an existing SDK capability or a requirement for Bridge.
