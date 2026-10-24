package com.sift.app.data

import com.sift.app.data.local.entity.StoredStats
import com.sift.app.data.mapper.IsoDateParser
import com.sift.app.data.mapper.toDomain
import com.sift.app.data.mapper.toEntity
import com.sift.app.data.mapper.toStored
import com.sift.app.data.remote.dto.AnalysisDto
import com.sift.app.data.remote.dto.AppDto
import com.sift.app.data.remote.dto.BacklogItemDto
import com.sift.app.data.remote.dto.CompetitorDto
import com.sift.app.data.remote.dto.ReviewDto
import com.sift.app.data.remote.dto.SourceDto
import com.sift.app.data.remote.dto.StatsDto
import com.sift.app.data.remote.dto.StepDto
import com.sift.app.data.remote.dto.ThemeDto
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.domain.model.BacklogType
import com.sift.app.domain.model.Effort
import com.sift.app.domain.model.ModelRole
import com.sift.app.domain.model.ReviewCategory
import com.sift.app.domain.model.Sentiment
import com.sift.app.domain.model.StepKey
import com.sift.app.domain.model.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoDateParserTest {
    @Test
    fun `parses utc offsets and fractions`() {
        assertEquals(1_714_644_900_000L, IsoDateParser.parse("2024-05-02T10:15:00Z"))
        assertEquals(1_714_644_900_123L, IsoDateParser.parse("2024-05-02T10:15:00.123456Z"))
        assertEquals(1_714_644_900_000L, IsoDateParser.parse("2024-05-02T12:15:00+02:00"))
        assertEquals(1_714_644_900_000L, IsoDateParser.parse("2024-05-02T03:15:00-0700"))
        assertEquals(1_714_608_000_000L, IsoDateParser.parse("2024-05-02"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(IsoDateParser.parse(null))
        assertNull(IsoDateParser.parse(""))
        assertNull(IsoDateParser.parse("yesterday"))
    }
}

class MapperTest {
    private val analysisDto = AnalysisDto(
        id = "a1", appName = "Notes", appId = "1", developer = "Dev", iconUrl = "https://i", source = "appstore",
        country = "us", competitors = listOf("Bear"), status = "running", currentStep = "classifying",
        progress = 140, error = null, warnings = null, createdAt = "2024-05-02T10:15:00Z",
        steps = listOf(
            StepDto("fetching", "done", null, null),
            StepDto("classifying", "running", "fast", "nvidia/Nemotron-Nano"),
            StepDto("unknown", "running", null, null)
        ),
        stats = StatsDto(
            totalReviews = 10, classifiedReviews = 8, averageRating = 3.4,
            sentiment = mapOf("positive" to 5, "neutral" to 1, "negative" to 2),
            categories = mapOf("bug" to 3, "nonsense" to 9),
            ratingDistribution = mapOf("1" to 2, "5" to 6),
            themeCount = 2, backlogCount = 3, competitorCount = 1
        )
    )

    @Test
    fun `analysis dto maps through entity to domain`() {
        val domain = analysisDto.toEntity().toDomain()
        assertEquals(AnalysisStatus.RUNNING, domain.status)
        assertEquals(StepKey.CLASSIFYING, domain.currentStep)
        assertEquals(100, domain.progress)
        assertEquals(1_714_644_900_000L, domain.createdAtMillis)
        assertEquals(2, domain.steps.size)
        assertEquals(StepState.RUNNING, domain.steps[1].state)
        assertEquals(ModelRole.FAST, domain.steps[1].modelRole)
        assertEquals(listOf(2, 0, 0, 0, 6), domain.stats.ratingDistribution)
        assertEquals(mapOf(ReviewCategory.BUG to 3), domain.stats.categories)
        assertEquals(2, domain.stats.sentiment.negative)
        assertTrue(domain.warnings.isEmpty())
        assertEquals(false, domain.isDemo)
    }

    @Test
    fun `demo analyses are marked and keep local fields`() {
        val previous = analysisDto.toEntity().copy(reportSynced = true)
        val entity = analysisDto.copy(createdAt = null).toEntity(previous = previous, isDemo = true)
        assertEquals(AnalysisSource.DEMO.key, entity.source)
        assertTrue(entity.isDemo)
        assertTrue(entity.reportSynced)
        assertEquals(previous.createdAtMillis, entity.createdAtMillis)
    }

    @Test
    fun `missing stats become zeros`() {
        val stored: StoredStats = (null as StatsDto?).toStored()
        assertEquals(List(5) { 0 }, stored.ratingDistribution)
        assertNull(stored.averageRating)
    }

    @Test
    fun `review dto validates ranges and drops remote id for samples`() {
        val dto = ReviewDto(
            id = 7, author = null, title = "t", text = "Crashes", rating = 9, reviewDate = "2024-05-02",
            appVersion = null, sentiment = "negative", category = "bug", severity = 0, summary = null,
            themeTitle = "Crashes"
        )
        val review = dto.toEntity("a1").toDomain()
        assertEquals(7L, review.remoteId)
        assertNull(review.rating)
        assertNull(review.severity)
        assertEquals(Sentiment.NEGATIVE, review.sentiment)
        assertEquals(ReviewCategory.BUG, review.category)
        assertEquals("", review.author)
        assertNull(dto.toEntity("a1", keepRemoteId = false).remoteId)
    }

    @Test
    fun `theme, backlog and competitor dtos map with safe defaults`() {
        val theme = ThemeDto(null, null, "T", "D", 4, 1.7, null).toEntity("a1", 3).toDomain()
        assertEquals(3, theme.position)
        assertEquals(1f, theme.negativeShare)

        val item = BacklogItemDto(
            null, null, "Fix", "bug", 150, "r", "u", "f", "XL", null, listOf("q")
        ).toEntity("a1", 0).toDomain()
        assertEquals(1, item.rank)
        assertEquals(100, item.priorityScore)
        assertEquals(BacklogType.BUG, item.type)
        assertEquals(Effort.M, item.effort)

        val competitor = CompetitorDto(
            null, "Bear", null, listOf("Fast"), null, null,
            listOf(SourceDto("Site", "https://bear.app"), SourceDto("Bad", "javascript:alert(1)"), SourceDto(null, null)),
            null
        ).toEntity("a1").toDomain()
        assertEquals(1, competitor.sources.size)
        assertEquals("https://bear.app", competitor.sources.single().url)
        assertEquals("", competitor.error)
    }

    @Test
    fun `search results without ids are dropped`() {
        assertNull(AppDto(null, "X", null, null, null, null, null).toDomain())
        assertEquals("Notes", AppDto("1", "Notes", null, null, 4.0, 3, null).toDomain()?.name)
    }
}
