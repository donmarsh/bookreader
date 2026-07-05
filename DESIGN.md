# Design System: The Scholarly Sanctuary

## 1. Overview & Creative North Star
**Creative North Star: "The Digital Curator"**

This design system is not a utility; it is a dedicated environment for deep focus and intellectual exploration. Unlike standard "flat" reader apps, this system draws inspiration from high-end editorial archives and private libraries. We move beyond the "template" look by prioritizing **intentional asymmetry**—offsetting headings to create rhythmic white space—and **tonal depth**. The interface should feel like an expensive linen-bound book: tactile, quiet, and authoritative. 

We reject the rigid, boxy constraints of traditional mobile grids in favor of **fluid, layered compositions** that allow content to breathe, ensuring the UI never competes with the literature it hosts.

---

## 2. Colors: Tonal Atmosphere
The palette is built on "Paper and Ink" philosophy, moving away from harsh pure whites and blacks.

### Surface Hierarchy & Nesting
We utilize a **Material-inspired nesting logic** where depth is defined by tonal shifts rather than lines.
- **Base Layer:** `surface` (#fcf9f4) serves as our "paper" foundation.
- **Nesting:** Place `surface_container_low` (#f6f3ee) for background sections and `surface_container_lowest` (#ffffff) for elevated cards. This creates a "sheet-on-sheet" effect that feels premium and tactile.

### The "No-Line" Rule
**1px solid borders are strictly prohibited for sectioning.** 
Boundaries must be defined solely through background color shifts. To separate a navigation rail from a library view, transition from `surface` to `surface_container`. This forces a softer, more sophisticated interface.

### The "Glass & Gradient" Rule
For floating elements (like a reading progress HUD), use semi-transparent `surface_variant` (#e5e2dd at 80% opacity) with a `24px` backdrop blur. 
*   **Signature Textures:** For primary CTAs, use a subtle linear gradient from `primary` (#114349) to `primary_container` (#2d5a61) at a 135-degree angle. This adds "soul" and a slight metallic sheen reminiscent of foil-stamped book spines.

---

## 3. Typography: The Editorial Voice
We use a dual-font system to separate "The Experience" (Reading) from "The Utility" (UI).

- **The Serif (Newsreader):** Used for all `display`, `headline`, `title`, and `body` scales. It carries the "Scholarly" weight. The high x-height and varied stroke widths mimic traditional typesetting.
- **The Sans-Serif (Manrope):** Used exclusively for `label` scales. Its geometric clarity provides a functional contrast to the organic serif, ensuring metadata (page numbers, file sizes) is legible at a glance.

**Hierarchy Strategy:**
- **Display-LG (3.5rem):** Use for book titles in hero states. Use tight tracking (-0.02em).
- **Body-LG (1rem):** Optimized for the reading experience with a generous line-height (1.6) to prevent eye fatigue.
- **Label-MD (0.75rem):** All-caps with 0.05em tracking for UI accents to provide a "cataloged" feel.

---

## 4. Elevation & Depth
Traditional shadows are too "digital." We use **Tonal Layering** and **Ambient Light**.

- **The Layering Principle:** Depth is achieved by "stacking" tiers. A book cover card (`surface_container_lowest`) sits on a shelf (`surface_container_low`), which sits on the floor (`surface`).
- **Ambient Shadows:** When a modal must float, use an extra-diffused shadow: 
  *   `0px 12px 32px rgba(28, 28, 25, 0.06)`. 
  *   Note the use of `on_surface` (#1c1c19) as the shadow base rather than pure black to maintain a soft, natural aesthetic.
- **The "Ghost Border" Fallback:** If a border is required for accessibility (e.g., an input field), use `outline_variant` (#c0c8c9) at **20% opacity**. It should be felt, not seen.

---

## 5. Components

### Buttons
- **Primary:** Gradient fill (`primary` to `primary_container`), `xl` (0.75rem) roundedness. No border.
- **Secondary:** `surface_container_high` fill with `primary` text.
- **Tertiary:** Text-only, using `primary` in Bold Newsreader.

### Input Fields
Avoid the "boxed" look. Use a `surface_container_lowest` fill with a `sm` (0.125rem) bottom-only "Ghost Border" that transitions to `primary` on focus. Use `manrope` for the input text to emphasize utility.

### Cards & Lists
**Strict Rule:** No divider lines between list items.
- Use `16px` of vertical white space to separate book entries.
- For "Library" views, use `surface_container_low` for the card background with an `xl` corner radius to make the book cover art the focal point.

### The "Reader HUD" (Custom Component)
A floating container using **Glassmorphism**. 
- **Fill:** `surface` at 70% opacity. 
- **Blur:** 20px. 
- **Edge:** A 1px "Ghost Border" at 10% opacity to catch the light.

### Chips
Small, `full` rounded capsules. Use `secondary_container` (#cfe7ec) with `on_secondary_container` (#52686d) text. These should look like soft library tags.

---

## 6. Do's and Don'ts

### Do
- **Do** use asymmetrical margins. Try a wider left margin (e.g., 32px) and a tighter right margin (24px) for headlines to mimic a book layout.
- **Do** lean into the "Dark Mode" `surface` (Charcoals/Muted Indigo). Ensure the `on_surface` text in dark mode is never #ffffff, but rather `inverse_on_surface` (#f3f0eb) to reduce contrast glare.
- **Do** use `tertiary` (#463b20) for "curated" or "staff pick" sections to give them a distinct, gold-leaf scholarly feel.

### Don't
- **Don't** use 100% opaque black for text. Always use `on_surface` (#1c1c19) for a softer, ink-like appearance.
- **Don't** use `DEFAULT` (0.25rem) roundedness for large containers; it looks too "standard." Use `xl` (0.75rem) or `none` for a more intentional, editorial look.
- **Don't** use standard "Success Green." Use the `primary` teal for positive actions to maintain the scholarly brand, reserving the `error` (#ba1a1a) tokens only for critical interruptions.