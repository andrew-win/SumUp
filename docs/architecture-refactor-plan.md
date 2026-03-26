# Architecture Refactor Plan (Clean Architecture + MVVM + SOLID + No Hardcode)

## Goals
- Strict layer boundaries: `presentation -> domain <- data`.
- Remove Android/framework dependencies from `domain`.
- Reduce god-classes and UI/business-logic mixing.
- Centralize constants/strings/keys/policies (no hidden hardcode).
- Make features testable and stable under change.

## Current Audit Snapshot
- Single-module app (`:app`) with ~100 Kotlin files.
- Critical oversized files:
  - `ui/screens/settings/SettingsScreen.kt` (~1722 lines)
  - `ui/screens/feed/FeedScreen.kt` (~1169 lines)
  - `ui/screens/settings/SettingsViewModel.kt` (~908 lines)
  - `ui/screens/sources/SourcesScreen.kt` (~763 lines)
  - `ui/screens/summary/SummaryScreen.kt` (~701 lines)
  - `data/repository/AiRepositoryImpl.kt` (~417 lines)
  - `domain/usecase/sources/GetSuggestedThemesUseCase.kt` (~413 lines)
  - `domain/usecase/ai/SummarizationEngineUseCase.kt` (~393 lines)
- Main risks found:
  - Presentation classes too large and include orchestration logic.
  - Domain contains business logic plus mixed formatting/hardcoded texts.
  - Several hardcoded messages and SharedPreferences keys are scattered.
  - Feature responsibilities are not fully isolated.

## Refactor Streams

### Stream A: Layer Purity (Clean Architecture)
1. Remove Android/SharedPreferences from `domain`.
2. Move persistence concerns to `data` repositories.
3. Keep `domain` with interfaces + pure policies/use-cases.

### Stream B: Presentation Decomposition (MVVM)
1. Split large screens into composable sections/components.
2. Move screen logic to dedicated `UiState`/`UiEvent`/`UiEffect`.
3. Keep Compose functions pure-render where possible.

### Stream C: SOLID and Responsibility Split
1. Split large use-cases into smaller collaborators.
2. Split large repositories by capabilities (prompting/parsing/rendering/caching).
3. Introduce explicit policy classes (formatting, dedup, language, ranking).

### Stream D: Hardcode Elimination
1. Move user-facing texts to resources.
2. Move constants/keys/time windows to central config/constants.
3. Unify serialization/parsing keys in one contract.

### Stream E: Reliability & Tests
1. Add unit tests for policies/use-cases.
2. Add parser contract tests for AI JSON pipelines.
3. Add integration tests for key repositories and workers.

## Execution Phases

### Phase 1 (Now): Foundation + Dependency Inversion
- [x] Audit and risk map.
- [x] First DIP refactor for recommended themes state:
  - Added `domain/repository/SuggestedThemesStateRepository`.
  - Added `data/repository/SuggestedThemesStateRepositoryImpl`.
  - Removed direct `Context/SharedPreferences` usage from:
    - `domain/usecase/feed/RefreshFeedUseCase.kt`
    - `domain/usecase/sources/GetSuggestedThemesUseCase.kt`
  - Updated DI in `di/AppModule.kt`.

### Phase 2: Presentation Split (Largest Files First)
1. `SettingsScreen.kt` -> section composables + state holders.
2. `FeedScreen.kt` -> article card, summary sheet, actions, filter panels.
3. `SummaryScreen.kt` -> timeline, scheduled block, infographic block.

### Phase 3: Domain Use-case Split
1. Break `SummarizationEngineUseCase` into:
  - Input preparation
  - Cloud execution + fallback policy
  - JSON parse/render policy
  - Source-block placement policy
2. Break `GetSuggestedThemesUseCase` into:
  - Eligibility policy
  - Embedding scoring policy
  - Recommendation threshold policy

### Phase 4: Data Layer Split
1. Split `AiRepositoryImpl` into internal collaborators:
  - request builder
  - response sanitizer/parser
  - prompt adapter
2. Introduce dedicated cache gateway for summary/session cache.

### Phase 5: Hardcode Cleanup
1. Move all remaining hardcoded UI/domain texts to resources/config.
2. Replace duplicated pref keys with one constants contract.
3. Normalize date/time and formatting policies.

### Phase 6: Test & Stabilization
1. Add/expand unit tests for new components.
2. Add regression tests for feed/single/scheduled summary flows.
3. Final cleanup and removal of dead/legacy paths.

## Definition of Done
- No Android framework imports in `domain`.
- No business logic in Compose screens.
- No user-facing hardcoded strings in Kotlin classes.
- Large files reduced and responsibilities explicit.
- Core flows covered by tests.
