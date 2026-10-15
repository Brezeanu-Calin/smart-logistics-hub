---
name: HaulMaster Telemetrics
colors:
  surface: '#121416'
  surface-dim: '#121416'
  surface-bright: '#38393c'
  surface-container-lowest: '#0c0e10'
  surface-container-low: '#1a1c1e'
  surface-container: '#1e2022'
  surface-container-high: '#282a2c'
  surface-container-highest: '#333537'
  on-surface: '#e2e2e5'
  on-surface-variant: '#d8c3ad'
  inverse-surface: '#e2e2e5'
  inverse-on-surface: '#2f3133'
  outline: '#a08e7a'
  outline-variant: '#534434'
  surface-tint: '#ffb95f'
  primary: '#ffc174'
  on-primary: '#472a00'
  primary-container: '#f59e0b'
  on-primary-container: '#613b00'
  inverse-primary: '#855300'
  secondary: '#ffca45'
  on-secondary: '#3f2e00'
  secondary-container: '#e4ae00'
  on-secondary-container: '#5b4400'
  tertiary: '#ffbcb7'
  on-tertiary: '#68000a'
  tertiary-container: '#ff938c'
  on-tertiary-container: '#8d0012'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffddb8'
  primary-fixed-dim: '#ffb95f'
  on-primary-fixed: '#2a1700'
  on-primary-fixed-variant: '#653e00'
  secondary-fixed: '#ffdf9a'
  secondary-fixed-dim: '#f7be1d'
  on-secondary-fixed: '#251a00'
  on-secondary-fixed-variant: '#5a4300'
  tertiary-fixed: '#ffdad7'
  tertiary-fixed-dim: '#ffb3ad'
  on-tertiary-fixed: '#410004'
  on-tertiary-fixed-variant: '#930013'
  background: '#121416'
  on-background: '#e2e2e5'
  surface-variant: '#333537'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-xl:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-xl-mobile:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  body-lg:
    fontFamily: Chivo
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Chivo
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.01em
  body-sm:
    fontFamily: Chivo
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.05em
  telemetry-data:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 20px
    letterSpacing: 0.02em
  telemetry-unit:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 12px
    letterSpacing: 0.08em
spacing:
  gutter: 0.75rem
  gutter-desktop: 1rem
  margin: 1rem
  margin-desktop: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system is engineered for industrial heavy-haul transport, freight logistics, and long-distance fleet operations. Designed to evoke the rugged reliability of continental cab-over trucks and commercial logistics dispatch consoles, the interface prioritizes extreme glanceability, immediate fault identification, and high-density operational telemetry.

The aesthetic fuses **Industrial Utilitarianism** and **Tactical HUD Instrumentation**. Visuals are resolutely flat, matte, and structural—rejecting glossy gradients, soft blurs, decorative drop shadows, and ornamental whitespace. Every element is enclosed in calibrated steel-and-asphalt framing, utilizing high-contrast safety yellow accents against dark asphalt backdrops to signal critical machine states, fuel consumption, load stress, route waypoints, and operational warnings. The atmosphere is that of an illuminated in-cab terminal cruising across trans-European motorways at 03:00.

## Colors

The palette operates under a high-contrast industrial safety framework built upon asphalt neutrals, hazard amber accents, and technical status indicators.

### Palette Architecture
- **Primary Canvas & Ground (`#0D0F11`):** Deep charcoal/asphalt matte base, simulating anti-glare dispatch hardware screens.
- **Surface Elevation 1 (`#161A1D`):** Docked panels, sidebars, cargo sheets, and telemetry rail containers.
- **Surface Elevation 2 (`#1F2428`):** Data tables, manifest row states, active card containers, and dialog shells.
- **Structural Dividers & Outlines (`#2D333B`, `#374151`):** Precise 1px borders providing architectural clarity across dense layouts.
- **Safety Amber Primary (`#F59E0B`, `#EAB308`):** Hazard yellow designated exclusively for interactive triggers, route milestones, primary CTA toggles, fuel critical thresholds, and dynamic focal points.
- **Active Focus & High Intensity (`#FACC15`):** High-luminance micro-accents, selected tab ticks, and tachometer redline thresholds.
- **Status Alert Matrix:**
    - **Critical Failure / Hazard:** `#EF4444` (Engine breakdown, axle overload, legal logbook violation).
    - **Nominal / In-Transit:** `#10B981` (Green transit corridors, cargo delivered, optimal tire pressure).
    - **Auxiliary Informational:** `#38BDF8` (GPS lock, ambient weather telemetry, CB channel active).
- **Text & Data Tiers:**
    - **High-Visibility Readout:** `#F3F4F6` (Primary manifests, vehicle identification, metric integers).
    - **Secondary Metadata:** `#9CA3AF` (Units of measure, route waypoints, driver status).
    - **Muted Structural Data:** `#6B7280` (Inactive timestamps, table headers, border notations).

Gradients and soft drop shadows are strictly prohibited. Surfaces must remain uniform, matte, and readable under variable ambient glare.

## Typography

The typographic hierarchy utilizes three specialized typefaces aligned to operational roles:

1. **Space Grotesk (Display & Section Headers):** Delivers a technical, sharp, geometric presence for station titles, system hub monitors, and cargo consignment labels.
2. **Chivo (Body & Descriptive Interface):** High legibility at compact sizes for freight details, manifest notes, driver log comments, and maintenance logs.
3. **JetBrains Mono (Telemetry, Readouts, and Structural Tags):** Reserved for numerical values, GPS coordinates, vehicle registration plates, VIN codes, tachograph clocks, and payload weights. Tabular figures ensure that shifting numeric readouts never cause UI reflow.

All telemetry numbers must feature aligned tabular figures (`font-variant-numeric: tabular-nums`) with accompanying unit tokens displayed in uppercase monospace micro-text.

## Layout & Spacing

This design system uses a compact, high-density 12-column fluid grid system engineered to present exhaustive datasets without excessive vertical scrolling.

### Layout Model
- **Grid Architecture:** 12 columns with standard 16px (`1rem`) gutters on desktop, collapsing to an 8-column layout with 12px (`0.75rem`) gutters on tablet, and a 4-column stack on mobile viewports.
- **Canvas Density:** Layouts rely on edge-to-edge modular docks. Fixed 64px vertical master-bars contain navigation, while operational consoles dock symmetrically to the left (manifest listings) and right (real-time vehicle diagnostic feeds).
- **Rhythm:** Spacing follows a 4px base increments. Spacers greater than 24px (`space-xl`) are strictly prohibited within core telemetry cards to prevent visual fragmentation. Data density takes precedence over ambient whitespace.

## Elevation & Depth

Visual hierarchy is maintained without drop shadows, glows, or depth blurs. Elevation is conveyed strictly through **Tonal Layering** and **Rigid Structural Outlines**.

### Structural Tiers
- **Ground Floor (`#0D0F11`):** The default screen canvas. Inset wells, inactive panels, and map viewport bases live here.
- **Mid Deck (`#161A1D`):** Elevated structural units, side-drawers, and telemetry clusters. Separated from the base solely via a 1px solid `#2D333B` line border.
- **Flight Deck / Active Tier (`#1F2428`):** Focused modules, modal inspect overlays, and hover-selected table rows. Separated by a 1px solid `#374151` line.
- **Hazard Intercept Focus:** For operational errors or emergency dispatch updates, panels gain a 1px solid `#F59E0B` structural frame, accompanied by an optional 3px left border accent.

No ambient or directional shadows are allowed; depth is entirely a construct of calibrated background luminescence and crisp 1px borders.

## Shapes

The design system enforces zero-radius corners (`0px`) throughout standard panels, tables, data chips, inputs, and action triggers.

- **Chamfered Geometries:** Certain primary action blocks, warning alerts, and HUD panel headers feature an industrial 45-degree corner notch (`clip-path: polygon(...)`) measuring 6px by 6px, reinforcing tactical diagnostic hardware aesthetics.
- **Dividers:** Horizontal and vertical dividers are rendered as strictly 1px continuous solid lines `#2D333B` or broken 2px dashed guides for segmented telemetry clusters.

## Components

### Buttons
- **Primary Industrial Trigger:** Solid `#F59E0B` fill, `#0D0F11` bold uppercase text (`JetBrains Mono`), 0px border radius, 8px 16px padding. Active hover state transitions to `#FACC15`.
- **Secondary Structural Trigger:** Background `#1F2428`, 1px solid border `#374151`, text `#F3F4F6`. Hover triggers border transition to `#F59E0B` with zero fill change.
- **Destructive / Abort Trigger:** Background `#1F2428`, 1px solid border `#EF4444`, text `#EF4444`. Hover switches to solid `#EF4444` with `#0D0F11` text.

### Telemetry Badges & Hazard Caution Stripes
- **Warning & Hazard Badges:** Components communicating route delay, toll violations, or overload feature a repeating 45-degree diagonal hazard caution stripe pattern (`repeating-linear-gradient(45deg, #F59E0B, #F59E0B 8px, #0D0F11 8px, #0D0F11 16px)`) rendered as a 4px accent banner or background framing.
- **Status Chips:** Solid `#161A1D` background, 1px solid border matching condition color (`#10B981` Nominal, `#F59E0B` Warning, `#EF4444` Critical), text in uppercase `JetBrains Mono` at `label-sm`.

### Tabular Logistics Manifests (Tables)
- **Header:** Sticky header row filled with `#161A1D`, 1px solid `#2D333B` bottom border. Text set in uppercase `label-sm` (`#9CA3AF`).
- **Data Rows:** Alternating row striping via `#0D0F11` and `#121518`. Height clamped to 36px for dense manifest scanning. 1px solid `#2D333B` border separating rows. Hover state applies an instant `#1F2428` highlight and a 2px `#F59E0B` left indicator.
- **Numeric Cells:** Right-aligned, formatted with tabular monospace metrics and secondary gray unit tags (e.g., `24,180` `KG`, `842` `KM`, `412` `L`).

### Input Fields & Selectors
- **Container:** Background `#161A1D`, 1px solid `#374151`, zero corner radius, text `#F3F4F6` in `Chivo`.
- **Focus State:** 1px solid `#F59E0B` outline, no outer glow.
- **Monospace Parameters:** Filter dials, license inputs, and GPS coordinate fields leverage `JetBrains Mono`.

### Checkboxes & Segmented Radios
- **Checkboxes:** 16x16px square, `#161A1D` background, 1px solid `#374151`. When checked, solid `#F59E0B` fill with `#0D0F11` sharp checkmark icon.
- **Segmented Radio Bar:** Inline technical toggle strip. Inactive segments `#161A1D`, 1px solid `#2D333B`, text `#9CA3AF`. Selected segment `#F59E0B`, text `#0D0F11` font-weight 700.

### Telemetry Metric Cards
- **Structure:** Background `#161A1D`, 1px solid `#2D333B`, header block with uppercase parameter label (e.g., `CHASSIS STRESS`, `RETARDER TEMP`) in `#9CA3AF`.
- **Readout:** Large monospace metric (`telemetry-data`) in `#F3F4F6` alongside unit badge (`#F59E0B`). Bottom 4px status line tracks visual threshold levels (green to amber to red).