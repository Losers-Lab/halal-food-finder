# Google listing photo extraction — method for subagents

Goal: for the given restaurant **location** (exact address + city), verify it resolves on Google Maps **at that specific address**, then capture a **high-res Google photo reference** suitable as a seed-listing hero image. Do NOT fabricate URLs.

## Rule (location match)
The photo/listing MUST be for the exact location given (specific branch/address). If Google Maps resolves a different branch or a renamed/altered venue, flag it — do not use a mismatched photo.

## Browsing method (Camofox browser on localhost:9377, up)
1. `browser_navigate` to:
   `https://www.google.com/maps/search/<URL-encoded name + full address + city>`
   e.g. `https://www.google.com/maps/search/The+Halal+Guys+5444+Lemmon+Ave+Dallas`
2. Read the snapshot. Confirm the **address** shown matches the target address exactly. If it doesn't, record `match:false` and STOP (not the right location / not found).
3. If it matches, open the photo viewer: click the "Photo of <Name>" button, or click a menu/food photo button (e.g. "Photo 1 of 12"). This navigates to a URL containing the photo reference.
4. `browser_console` with expression:
   `[...document.querySelectorAll('img')].map(i=>i.src).filter(s=>s&&s.includes('lh3.googleusercontent.com/gps-cs-s/')).map(u=>u.split('=')[0])` — returns unique photo **CDN IDs** (strip the size suffix).
   Prefer the photo associated with the largest original (the gallery usually includes one large landscape hero + several small portrait thumbnails).
5. High-res URL format for a given CDN ID `XXXX`:
   `https://lh3.googleusercontent.com/gps-cs-s/XXXX=w1200-k-no`
   (The `=w1200-k-no` suffix requests ~1200px wide, no cropping.)

## Output format (one per location, JSON per line)
```json
{"name": "<restaurant>", "city": "<city>", "address_given": "<target addr>", "address_found": "<address Google confirmed>", "match": true/false, "place_verified": "<what the page said, e.g. 5444 Lemmon Ave, Dallas TX 75209>", "photo_cdn_id": "<ID or null>", "photo_xlarge": true/false, "hero_url": "<final w1200 url or null>", "note": "<any caveat>"}
```

## Important
- If no photo reference is obtainable, set hero_url=null and note why. Do not invent.
- If the resolved place is a different branch/venue, set match=false and hero_url=null.
- The place ID / coordinates in the navigation URL are useful confirmation of exact location.
- Google may require a moment to hydrate; navigate, then snapshot, then console.