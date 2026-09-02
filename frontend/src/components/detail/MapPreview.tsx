/**
 * MapPreview — embedded map preview with a location pin (docs/design/
 * detail-page.md §1.3). Small, secondary widget inside the Location sidebar
 * (founder dislikes map-FIRST, but a detail-page preview is acceptable).
 *
 * Uses the Google Maps oEmbed URL (`?q=<lat>,<lng>&output=embed`), the same
 * provider the "Get directions" deep-link already targets, so the map and the
 * directions link describe the same place. Requires no API key. The listing
 * read surface already carries `lat`/`lng`, so no backend change is needed
 * (sc-187).
 *
 * The iframe is rendered only when both coordinates are finite — the read
 * surface treats lat/lng as required numbers, but a bad value must degrade to
 * no map, never a broken frame.
 */
export function MapPreview({
  lat,
  lng,
  restaurantName,
}: {
  /** Backend-listed latitude (listing read surface). */
  lat: number;
  /** Backend-listed longitude (listing read surface). */
  lng: number;
  /** Restaurant name — used for the accessible iframe title. */
  restaurantName: string;
}) {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return (
    <div className="overflow-hidden rounded-lg border border-kraft-200 shadow-card">
      <iframe
        title={`Map showing the location of ${restaurantName}`}
        src={`https://www.google.com/maps?q=${lat},${lng}&z=16&output=embed`}
        className="block h-64 w-full border-0"
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        allowFullScreen
      />
    </div>
  );
}