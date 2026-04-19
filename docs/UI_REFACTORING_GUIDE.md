# UI Refactoring Guide

## Purpose
- Keep screens small enough to reason about.
- Reuse one visual language for cards, controls, dialogs, top bars, and state screens.
- Prevent reintroducing one-off UI patterns directly inside large screen files.

## File Structure
- `Screen.kt`: route/root orchestration only.
- `...Content...kt`: user-facing content blocks for a screen.
- `...History...kt` / `...Dashboard...kt` / `...Dialogs...kt`: isolated subdomains.
- `ui/components/`: app-wide reusable building blocks.
- `ui/theme/`: tokens and visual primitives only.

## Size Rules
- Prefer keeping root screen files under ~500-700 lines.
- When a screen mixes state wiring and large visual blocks, move visual blocks out first.
- If a file contains multiple dialogs, move dialogs into a dedicated file.

## Shared Patterns
- Use `AppTopBar`, `AppHelpToggleAction`, `AppSelectionActions`, `AppProminentFab`, `AppBackToTopFab` for common screen-shell behavior.
- Use `AppSearchField`, `AppFilterMenuChip`, `AppExportPdfButton` for search/filter/export rows.
- Use `AppCardSurface` or theme helpers from `CardStyles.kt` for standard container cards.
- Use shared settings controls from `SettingsControlComponents.kt` instead of rebuilding toggle/slider rows.

## Card Rules
- Standard content cards should use shared card shape/border/colors.
- Selected, error, or accent cards may override color/border, but should still start from shared card primitives.
- Avoid repeating `RoundedCornerShape(18.dp)` and manual `outlineVariant.copy(alpha = ...)` when a shared helper already exists.

## Settings Rules
- `SettingsScreen.kt` should coordinate state and routing only.
- Group-specific UI belongs in dedicated `...GroupContent` composables.
- Repeated controls must go through shared rows/items before adding more inline `Switch` / `Slider` blocks.

## Summary Rules
- Keep `SummaryScreen.kt` as orchestration.
- History, dashboard, content, and block rendering should stay split into dedicated files.
- Inline source rendering should stay centralized so link behavior remains consistent.

## Review Checklist
- Is this UI pattern already implemented elsewhere?
- Can this block become a reusable component instead of another inline branch?
- Does the new code follow shared card, control, and screen-shell patterns?
- Did the root screen get simpler after the change?
