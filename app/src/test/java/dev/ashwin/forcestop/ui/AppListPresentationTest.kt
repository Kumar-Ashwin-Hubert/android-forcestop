package dev.ashwin.forcestop.ui

import dev.ashwin.forcestop.data.InstalledApp
import dev.ashwin.forcestop.service.AppTarget
import dev.ashwin.forcestop.service.StopOutcome
import dev.ashwin.forcestop.service.StopResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListPresentationTest {
    private val regular = InstalledApp("dev.chat", "Chat", null, false)
    private val system = InstalledApp("dev.settings", "Settings", null, true)
    private val other = InstalledApp("dev.other", "Other", null, false)
    private val apps = listOf(regular, system, other)

    @Test
    fun selectedViewIncludesSelectedSystemAppsButNoOtherApps() {
        assertEquals(
            listOf(system),
            filterApps(apps, setOf(system.packageName), "", false, true),
        )
    }

    @Test
    fun allViewHidesOnlyUnselectedSystemApps() {
        assertEquals(listOf(regular, other), filterApps(apps, emptySet(), "", false, false))
        assertEquals(apps, filterApps(apps, setOf(system.packageName), "", false, false))
        assertEquals(apps, filterApps(apps, emptySet(), "", true, false))
    }

    @Test
    fun searchMatchesNamesAndPackagesIgnoringCaseAndSurroundingWhitespace() {
        assertEquals(listOf(regular), filterApps(apps, emptySet(), " CHAT ", false, false))
        assertEquals(listOf(other), filterApps(apps, emptySet(), "DEV.OTHER", false, false))
    }

    @Test
    fun searchDoesNotExposeUnselectedAppsInSelectedViewOrChangeSelection() {
        val selected = setOf(regular.packageName, system.packageName)
        assertEquals(emptyList<InstalledApp>(), filterApps(apps, selected, "Other", true, true))
        assertEquals(setOf(regular.packageName, system.packageName), selected)
        assertEquals(listOf(regular, system), filterApps(apps, selected, "", false, true))
    }

    @Test
    fun selectedViewIsEmptyAfterClearingSelection() {
        assertEquals(emptyList<InstalledApp>(), filterApps(apps, emptySet(), "", true, true))
    }

    @Test
    fun packageNameShowsOnlyWhenTheLabelIsNotEnoughOnItsOwn() {
        val duplicate = InstalledApp("dev.chat.work", "Chat", null, false)
        val ambiguous = ambiguousLabels(apps + duplicate)

        assertEquals(setOf("chat"), ambiguous)
        assertTrue(shouldShowPackage(regular, "", ambiguous))
        assertTrue(shouldShowPackage(duplicate, "", ambiguous))
        assertFalse(shouldShowPackage(other, "", ambiguous))
    }

    @Test
    fun packageNameShowsWhenTheSearchMatchedThePackageRatherThanTheLabel() {
        val none = emptySet<String>()
        assertTrue(shouldShowPackage(other, "dev.other", none))
        assertFalse(shouldShowPackage(other, " OTHER ", none))
        assertFalse(shouldShowPackage(other, "  ", none))
    }

    @Test
    fun subtitleKeepsTheSystemMarkerIndependentOfThePackageName() {
        assertEquals("System", rowSubtitle(system, showPackage = false))
        assertEquals("System \u00b7 dev.settings", rowSubtitle(system, showPackage = true))
        assertEquals("dev.other", rowSubtitle(other, showPackage = true))
        assertNull(rowSubtitle(other, showPackage = false))
    }

    @Test
    fun resultHeadlinePrioritizesFailures() {
        val results = listOf(
            StopResult(AppTarget("one", "One"), StopOutcome.STOPPED),
            StopResult(AppTarget("two", "Two"), StopOutcome.ALREADY_STOPPED),
            StopResult(AppTarget("three", "Three"), StopOutcome.FAILED, "Timed out"),
            StopResult(AppTarget("four", "Four"), StopOutcome.STOPPED),
        )
        assertEquals("1 app couldn't be stopped", resultHeadline(results))
        assertEquals(
            "2 apps couldn't be stopped",
            resultHeadline(results + StopResult(AppTarget("five", "Five"), StopOutcome.FAILED)),
        )
    }

    @Test
    fun failuresComeFirstWithoutReorderingPeersOrMutatingResults() {
        val stopped = StopResult(AppTarget("one", "One"), StopOutcome.STOPPED)
        val failed = StopResult(AppTarget("two", "Two"), StopOutcome.FAILED)
        val alreadyStopped = StopResult(AppTarget("three", "Three"), StopOutcome.ALREADY_STOPPED)
        val secondFailure = StopResult(AppTarget("four", "Four"), StopOutcome.FAILED)
        val results = listOf(stopped, failed, alreadyStopped, secondFailure)
        assertEquals(listOf(failed, secondFailure, stopped, alreadyStopped), resultsWithFailuresFirst(results))
        assertEquals(listOf(stopped, failed, alreadyStopped, secondFailure), results)
    }

    @Test
    fun headlineHandlesEmptyAndAlreadyStoppedRuns() {
        assertEquals("No apps processed", resultHeadline(emptyList()))
        assertEquals(
            "1 app stopped",
            resultHeadline(listOf(StopResult(AppTarget("one", "One"), StopOutcome.ALREADY_STOPPED))),
        )
    }

    @Test
    fun headlineCombinesBothSuccessfulOutcomes() {
        val stopped = StopResult(AppTarget("one", "One"), StopOutcome.STOPPED)
        val alreadyStopped = StopResult(AppTarget("two", "Two"), StopOutcome.ALREADY_STOPPED)
        assertEquals("All 2 apps are stopped", resultHeadline(listOf(stopped, alreadyStopped)))
        assertEquals(
            "All 3 apps are stopped",
            resultHeadline(listOf(stopped, alreadyStopped, stopped.copy(target = AppTarget("three", "Three")))),
        )
    }

    @Test
    fun summaryAndRowsOnlyDistinguishStoppedAndFailed() {
        val results = listOf(
            StopResult(AppTarget("one", "One"), StopOutcome.STOPPED),
            StopResult(AppTarget("two", "Two"), StopOutcome.ALREADY_STOPPED),
            StopResult(AppTarget("three", "Three"), StopOutcome.FAILED),
        )
        assertEquals("2 stopped \u00b7 1 failed", resultSummary(results))
        assertEquals("0 stopped \u00b7 0 failed", resultSummary(emptyList()))
        assertEquals("0 stopped \u00b7 1 failed", resultSummary(listOf(results.last())))
        assertEquals("2 stopped \u00b7 0 failed", resultSummary(results.take(2)))
        assertEquals("Stopped", StopOutcome.STOPPED.summary())
        assertEquals("Stopped", StopOutcome.ALREADY_STOPPED.summary())
        assertEquals("Failed", StopOutcome.FAILED.summary())
    }
}