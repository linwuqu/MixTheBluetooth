# Migratedev Workflow Topology Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the early migratedev planning skeleton with a compiling topology-first workflow skeleton for authentication and device connection.

**Architecture:** Packages follow the SOP topology at the first level: UI, Translation, Orchestrator, DecisionCore, and Port. One generic `WorkflowOrchestrator` owns the event loop and state publication; business code supplies a pure `DecisionCore` and an `EffectExecutor` that maps effects through ports back into events.

**Tech Stack:** Kotlin 1.9.24, kotlinx.coroutines Flow/StateFlow, Android Gradle Plugin 8.5.2, JUnit 4, kotlinx-coroutines-test.

---

### Task 1: Enable Kotlin workflow tests

**Files:**
- Modify: `migratedev/build.gradle`

- [x] Apply the Kotlin Android plugin, align Java/Kotlin JVM targets at 17, and add `kotlinx-coroutines-core` plus `kotlinx-coroutines-test`.
- [x] Run `./gradlew :migratedev:testDebugUnitTest` and record the existing baseline before introducing workflow code.

### Task 2: Specify the common event loop with a failing test

**Files:**
- Create: `migratedev/src/test/java/com/biosensor/migratedev/orchestrator/WorkflowOrchestratorTest.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/decisioncore/DecisionContracts.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/orchestrator/OrchestratorContracts.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/orchestrator/WorkflowOrchestrator.kt`

- [x] Write a test where `dispatch(Start)` reduces to `Running` plus an effect and the effect emits `Finished`, which must return through the same event queue and produce `Done`.
- [x] Run the focused test and verify it fails because the workflow contracts do not exist.
- [x] Add `DecisionCore<State, Event, Effect>.reduce`, `Transition<State, Effect>`, and `EffectExecutor<Effect, Event>.execute(effect): Flow<Event>`.
- [x] Implement one `WorkflowOrchestrator` with `Channel<Event>`, `StateFlow<State>`, and runtime-owned effect jobs; expose only `dispatch` and `close`.
- [x] Run the focused test and verify the state reaches `Done`.

### Task 3: Add the authentication workflow

**Files:**
- Create: `migratedev/src/test/java/com/biosensor/migratedev/decisioncore/auth/AuthDecisionCoreTest.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/decisioncore/auth/AuthDecision.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/port/auth/AuthPort.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/port/PortContracts.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/orchestrator/auth/AuthEffectExecutor.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/translation/TranslationContracts.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/translation/auth/AuthTranslation.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/ui/auth/AuthScreen.kt`

- [x] Write reducer tests for login submission, accepted remote session, persisted session, rejection, and logout.
- [x] Run the focused tests and verify they fail because `AuthDecisionCore` is missing.
- [x] Put `User`, `AuthSession`, `AuthState`, `AuthEvent`, `AuthEffect`, and the reducer together in `AuthDecision.kt`.
- [x] Put `AuthCommand`, `AuthResult`, and `AuthPort` together in `AuthPort.kt`, using `execute(command): Flow<AuthResult>`.
- [x] Implement `AuthEffectExecutor` as the only business mapping from Effect to Command and Result to Event.
- [x] Implement `AuthTranslation` with Intent/UiState mapping and delegation to the common orchestrator; leave `AuthScreen.kt` as an explicit UI placeholder.
- [x] Run the authentication tests and verify they pass.

### Task 4: Add the device connection workflow

**Files:**
- Create: `migratedev/src/test/java/com/biosensor/migratedev/decisioncore/connection/ConnectionDecisionCoreTest.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/decisioncore/connection/ConnectionDecision.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/port/bluetooth/BluetoothPort.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/orchestrator/connection/ConnectionEffectExecutor.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/translation/connection/ConnectionTranslation.kt`
- Create: `migratedev/src/main/java/com/biosensor/migratedev/ui/connection/ConnectionScreen.kt`

- [x] Write reducer tests for starting/stopping scan, selecting a remembered device, successful connection, failure, timeout, and disconnect.
- [x] Run the focused tests and verify they fail because `ConnectionDecisionCore` is missing.
- [x] Put `BluetoothDeviceInfo`, state, event, effect, and reducer together in `ConnectionDecision.kt`.
- [x] Put connection command/result contracts and `BluetoothPort` together in `BluetoothPort.kt`.
- [x] Implement the standard Effect/Command/Result/Event mapping in `ConnectionEffectExecutor`.
- [x] Implement `ConnectionTranslation` and the UI placeholder.
- [x] Run the connection tests and verify they pass.

### Task 5: Remove obsolete planning and verify the module

**Files:**
- Delete: `migratedev/src/main/java/com/biosensor/migratedev/business/**`
- Delete: `migratedev/src/main/java/com/biosensor/migratedev/promise/**`
- Delete: `migratedev/src/test/java/com/biosensor/migratedev/ExampleUnitTest.java`

- [x] Remove only the previously listed early planning files under `business` and `promise`.
- [x] Run `./gradlew :migratedev:testDebugUnitTest`.
- [x] Run `./gradlew :migratedev:assembleDebug`.
- [x] Run `git diff --check` and inspect the final topology tree and diff.
