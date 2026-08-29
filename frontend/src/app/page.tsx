import {
  HandCutIndicator,
  MachineCutIndicator,
  UnverifiedTag,
  VerifiedBadge,
} from "@/components/trust";

/**
 * Static design-system preview (skeleton task). Renders the trust components
 * per docs/design/trust-components.md so tokens + components are visually
 * inspectable. No data wiring.
 */
export default function Home() {
  return (
    <main className="mx-auto max-w-3xl px-5 py-12">
      <h1 className="text-display text-neutral-900">Halal Food Finder</h1>
      <p className="mt-2 text-body text-neutral-500">
        Design-system preview — trust components and tokens (docs/design).
      </p>

      <section className="mt-10">
        <h2 className="text-title text-neutral-900">Trust components</h2>

        <div className="mt-4 flex flex-wrap items-center gap-4">
          <VerifiedBadge />
          <UnverifiedTag />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-4">
          <HandCutIndicator />
          <MachineCutIndicator />
        </div>
      </section>

      <section className="mt-10">
        <h2 className="text-title text-neutral-900">Composition (listing card)</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div className="rounded-lg border border-neutral-200 bg-neutral-0 p-5 shadow-card">
            <div className="flex items-center justify-between gap-2">
              <span className="text-heading text-neutral-900">Al-Amir Grill</span>
              <VerifiedBadge />
            </div>
            <p className="mt-1 text-small text-neutral-500">
              Middle Eastern · 1.2 mi · ★ 4.6 (89)
            </p>
            <div className="mt-3">
              <HandCutIndicator />
            </div>
          </div>
          <div className="rounded-lg border border-neutral-200 bg-neutral-0 p-5 shadow-card">
            <div className="flex items-center justify-between gap-2">
              <span className="text-heading text-neutral-900">Saffron House</span>
              <UnverifiedTag />
            </div>
            <p className="mt-1 text-small text-neutral-500">
              South Asian · 0.8 mi · ★ 4.2 (31)
            </p>
            <div className="mt-3">
              <MachineCutIndicator />
            </div>
          </div>
        </div>
      </section>

      <section className="mt-10">
        <h2 className="text-title text-neutral-900">Tokens</h2>
        <div className="mt-4 flex flex-wrap gap-4">
          {(
            [
              ["brand-500", "bg-brand-500"],
              ["positive-500", "bg-positive-500"],
              ["neutral-900", "bg-neutral-900"],
              ["neutral-200", "bg-neutral-200"],
              ["danger-500", "bg-danger-500"],
              ["warning-500", "bg-warning-500"],
            ] as const
          ).map(([name, cls]) => (
            <div key={name} className="flex flex-col items-center gap-1">
              <div className={`h-10 w-10 rounded-md border border-neutral-200 ${cls}`} />
              <span className="text-small text-neutral-500">{name}</span>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
