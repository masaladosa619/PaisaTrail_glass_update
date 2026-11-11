package com.paisatrail.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisatrail.domain.insights.Insights
import com.paisatrail.domain.insights.Pacing
import com.paisatrail.domain.model.Category
import com.paisatrail.domain.util.Money
import com.paisatrail.ui.components.AnimatedAmount
import com.paisatrail.ui.components.ChartSlice
import com.paisatrail.ui.components.DonutChart
import com.paisatrail.ui.components.EmptyState
import com.paisatrail.ui.components.FilterPill
import com.paisatrail.ui.components.Pill
import com.paisatrail.ui.components.ProgressBar
import com.paisatrail.ui.components.SectionCard
import com.paisatrail.ui.components.SectionLabel
import com.paisatrail.ui.components.TransactionRow
import com.paisatrail.ui.components.bouncyClickable
import com.paisatrail.ui.components.breathe
import com.paisatrail.ui.components.color
import com.paisatrail.ui.components.glass
import com.paisatrail.ui.components.riseIn
import com.paisatrail.ui.components.shimmer
import com.paisatrail.ui.theme.AppColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHEET_PEEK = 290.dp
private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

@Composable
fun HomeScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenPlanner: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aiTip by viewModel.aiTip.collectAsStateWithLifecycle()
    val hasAiKey by viewModel.hasAiKey.collectAsStateWithLifecycle()

    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = remember(state.transactions, typeFilter, categoryFilter) {
        state.transactions.filter { txn ->
            (typeFilter == "ALL" || txn.type.name == typeFilter) &&
                (categoryFilter == null || txn.category.name == categoryFilter)
        }
    }

    BottomSheetScaffold(
        sheetPeekHeight = SHEET_PEEK,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // Tinted enough to stay readable while the dashboard scrolls underneath it.
        sheetContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterPill("ALL", typeFilter == "ALL", { typeFilter = "ALL" }) }
                    item { FilterPill("EXPENSES", typeFilter == "EXPENSE", { typeFilter = "EXPENSE" }) }
                    item { FilterPill("INCOME", typeFilter == "INCOME", { typeFilter = "INCOME" }) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Category.entries.toList()) { category ->
                        FilterPill(
                            text = category.label.uppercase(),
                            selected = categoryFilter == category.name,
                            onClick = {
                                categoryFilter = if (categoryFilter == category.name) null else category.name
                            },
                            dotColor = category.color()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.ReceiptLong,
                        title = if (state.transactions.isEmpty()) "Nothing logged this month" else "No matches",
                        body = if (state.transactions.isEmpty()) {
                            "Share a payment screenshot to PaisaTrail, import a statement, or tap + to add one by hand."
                        } else {
                            "No transactions match these filters."
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, txn -> txn.id }) { index, txn ->
                            TransactionRow(
                                txn = txn,
                                onClick = { onOpenTransaction(txn.id) },
                                modifier = Modifier.animateItem().riseIn(index)
                            )
                        }
                    }
                }
            }
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your monthly spend",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).riseIn(0)
                )
                MonthSwitcher(
                    month = state.month,
                    canGoForward = !state.isCurrentMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }

            if (state.incomePaise > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val used = state.expenseTotalPaise * 100.0 / state.incomePaise
                Pill(
                    text = String.format(Locale.US, "%.1f%% INCOME USED", used),
                    containerColor = if (used > 100) AppColors.Expense else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (used > 100) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.riseIn(1)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // Rolls up from the previous figure whenever the month or the data changes.
                AnimatedAmount(
                    paise = state.expenseTotalPaise,
                    fontSize = 40.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (state.incomePaise > 0) {
                    Text(
                        "/" + Money.format(state.incomePaise, false),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
                    )
                }
            }

            val delta = state.expenseTotalPaise - state.previousExpensePaise
            if (state.previousExpensePaise > 0 || state.expenseTotalPaise > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Pill(
                    text = when {
                        delta > 0 -> "Expenses up by +${Money.format(delta, false)}"
                        delta < 0 -> "Expenses down by -${Money.format(-delta, false)}"
                        else -> "Same as last month"
                    },
                    containerColor = if (delta > 0) AppColors.Expense.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (delta > 0) AppColors.Expense else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.riseIn(2)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            SpendBreakdown(state)

            Spacer(modifier = Modifier.height(16.dp))
            BudgetCard(state = state, onOpenPlanner = onOpenPlanner)

            state.insights?.let { insights ->
                Spacer(modifier = Modifier.height(12.dp))
                InsightsCard(insights = insights, aiTip = aiTip, hasAiKey = hasAiKey, onAskAi = viewModel::askAi)
            }

            Spacer(modifier = Modifier.height(SHEET_PEEK + 24.dp))
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.glass(CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ChevronLeft,
            contentDescription = "Previous month",
            modifier = Modifier.bouncyClickable(shape = CircleShape, onClick = onPrevious).padding(6.dp).size(20.dp)
        )
        // The label slides up for a later month and down for an earlier one.
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val later = targetState > initialState
                (slideInVertically(tween(260)) { if (later) it else -it } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(260)) { if (later) -it else it } + fadeOut(tween(160)))
            },
            label = "monthLabel"
        ) { shown ->
            Text(MONTH_LABEL.format(shown), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Next month",
            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .bouncyClickable(shape = CircleShape, enabled = canGoForward, onClick = onNext)
                .padding(6.dp)
                .size(20.dp)
        )
    }
}

@Composable
private fun SpendBreakdown(state: HomeUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DonutChart(
            slices = state.byCategory.map { ChartSlice(it.amountPaise.toFloat(), it.category.color()) },
            emptyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (state.byCategory.isEmpty()) {
                Text(
                    "Your category split shows up here once you log a spend.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            state.byCategory.take(7).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.riseIn(index + 1)) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(entry.category.color()))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(entry.category.label, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        Money.format(entry.amountPaise, false),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.byCategory.size > 7) {
                Text(
                    "+${state.byCategory.size - 7} more",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetCard(state: HomeUiState, onOpenPlanner: () -> Unit) {
    SectionCard(
        modifier = Modifier.bouncyClickable(shape = RoundedCornerShape(22.dp), onClick = onOpenPlanner),
        enterIndex = 3
    ) {
        if (state.budgetPaise <= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Set one in Planner", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        } else {
            val left = state.budgetPaise - state.expenseTotalPaise
            val over = left < 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    if (over) "${Money.format(-left, false)} over" else "${Money.format(left, false)} left",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (over) AppColors.Expense else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ProgressBar(
                fraction = state.expenseTotalPaise.toFloat() / state.budgetPaise.toFloat(),
                color = if (over) AppColors.Expense else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InsightsCard(insights: Insights, aiTip: AiTip, hasAiKey: Boolean, onAskAi: () -> Unit) {
    SectionCard(enterIndex = 4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = AppColors.Warning,
                modifier = Modifier.size(16.dp).breathe()
            )
            Spacer(modifier = Modifier.width(6.dp))
            SectionLabel("Insights")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(insights.summary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        val bannerColor = when (insights.pacing) {
            Pacing.UNDER -> MaterialTheme.colorScheme.primaryContainer
            Pacing.OVER -> AppColors.Warning
            Pacing.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
        }
        val bannerText = when (insights.pacing) {
            Pacing.UNDER -> MaterialTheme.colorScheme.onPrimaryContainer
            Pacing.OVER -> Color(0xFF241A00)
            Pacing.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            insights.pacingMessage,
            color = bannerText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bannerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (hasAiKey) {
            Spacer(modifier = Modifier.height(6.dp))
            when (aiTip) {
                AiTip.Idle -> TextButton(onClick = onAskAi) { Text("Ask Gemini for a tip") }
                AiTip.Loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Thinking…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.shimmer()
                    )
                }
                is AiTip.Ready -> Text(aiTip.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                AiTip.Failed -> TextButton(onClick = onAskAi) { Text("Couldn't reach Gemini. Try again") }
            }
        }
    }
}
