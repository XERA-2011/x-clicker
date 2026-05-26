# Phase 1: Core Engine Design (clickCenter & Loop Prevention)

## Overview
This document specifies the design for Phase 1 of migrating high-value features from GKD into x-clicker's core background engine. The primary goals are:
1. Implement coordinate-based physical clicking (`clickCenter`) to bypass accessibility restrictions.
2. Implement robust loop prevention mechanisms (`actionCd` and `actionMaximum`) to ensure service stability.

## Architecture & Data Flow

### 1. `clickCenter` Execution
Many apps disable `isClickable` on ad skip buttons to defeat standard `AccessibilityService.ACTION_CLICK`. We will implement a `dispatchGesture`-based fallback.

**Implementation Details:**
- **Extension Function:** `AccessibilityNodeInfo.performClickCenter(service: AccessibilityService): Boolean`
- **Coordinate Calculation:**
  - `getBoundsInScreen(rect)` to retrieve absolute screen bounds.
  - `centerX = rect.exactCenterX()`, `centerY = rect.exactCenterY()`
- **Gesture Dispatch:**
  - Create a `Path` moving to `(centerX, centerY)`.
  - Build `GestureDescription.StrokeDescription` with a duration of `50L` ms to simulate a fast tap.
  - Call `service.dispatchGesture`.
- **Integration in `XClickerService.queryAndAct`:**
  - If `rule.action == "clickCenter"`, bypass `performAction` and trigger `performClickCenter`.
  - Provide a fallback mechanism if `"click"` is requested but fails.

### 2. Anti-Loop State Machine (`actionCd` / `actionMaximum`)
Rules need bounded execution rates to avoid phone freezing loops.

**Data Structures:**
```kotlin
data class RuleState(
    var lastExecutionTime: Long = 0,
    var executionCount: Int = 0
)
```
- In `XClickerService`, maintain `private val ruleStates = ConcurrentHashMap<Int, RuleState>()` mapping `rule.key` to its state.

**Execution Interceptor (inside `queryAndAct`):**
Before committing to click a matched node:
1. **Cooldown Check:** `currentTime - state.lastExecutionTime < (rule.actionCd ?: 1000L)` -> return false.
2. **Maximum Trigger Check:** `state.executionCount >= (rule.actionMaximum ?: Int.MAX_VALUE)` -> return false.
3. **On Success:** update `lastExecutionTime = currentTime`, `executionCount++`.

**State Reset Logic:**
GKD supports `resetMatch` strings: "activity", "app", "match".
- In `onAccessibilityEvent` when tracking `WindowStateChanged`:
  - If Activity changes, clear states for rules with `resetMatch == "activity"`.
  - If App package changes, clear states for rules with `resetMatch == "app"`.

## Testing & Verification
- **Unit Testing:** Expand `XClickerService` logical testing to verify the `ConcurrentHashMap` correctly intercepts high-frequency calls.
- **Manual Verification:** Test against a mock UI or known problematic app to verify `dispatchGesture` correctly triggers bounds that refuse `ACTION_CLICK`.
