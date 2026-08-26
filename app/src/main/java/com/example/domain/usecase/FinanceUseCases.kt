package com.example.domain.usecase

import android.content.Context
import com.example.domain.repository.FinanceRepository

class FinanceUseCases(
    val repository: FinanceRepository,
    val calculateDcfFairValue: CalculateDcfFairValueUseCase,
    val calculateBuffettShortcut: CalculateBuffettShortcutUseCase,
    val calculateEffectiveCostBasis: CalculateEffectiveCostBasisUseCase,
    val searchTickers: SearchTickersUseCase,
    val syncTickerData: SyncTickerDataUseCase,
    val forceGeminiSync: ForceGeminiSyncUseCase,
    val getWatchlist: GetWatchlistUseCase,
    val addTicker: AddTickerUseCase,
    val removeTicker: RemoveTickerUseCase,
    val updateManualCostBasis: UpdateManualCostBasisUseCase,
    val updateTargetPrice: UpdateTargetPriceUseCase,
    val updateWatchlistOrder: UpdateWatchlistOrderUseCase,
    val getAllTrades: GetAllTradesUseCase,
    val getTradesForTicker: GetTradesForTickerUseCase,
    val logTrade: LogTradeUseCase,
    val updateTrade: UpdateTradeUseCase,
    val deleteLoggedTrade: DeleteLoggedTradeUseCase,
    val getCalculatorSnapshot: GetCalculatorSnapshotUseCase,
    val getCalculatorSnapshotFlow: GetCalculatorSnapshotFlowUseCase,
    val updateCalculatorSnapshot: UpdateCalculatorSnapshotUseCase
) {
    companion object {
        fun createDefault(context: Context, repository: FinanceRepository): FinanceUseCases {
            return FinanceUseCases(
                repository = repository,
                calculateDcfFairValue = CalculateDcfFairValueUseCase(),
                calculateBuffettShortcut = CalculateBuffettShortcutUseCase(),
                calculateEffectiveCostBasis = CalculateEffectiveCostBasisUseCase(),
                searchTickers = SearchTickersUseCase(repository),
                syncTickerData = SyncTickerDataUseCase(repository),
                forceGeminiSync = ForceGeminiSyncUseCase(repository),
                getWatchlist = GetWatchlistUseCase(repository),
                addTicker = AddTickerUseCase(repository),
                removeTicker = RemoveTickerUseCase(repository),
                updateManualCostBasis = UpdateManualCostBasisUseCase(repository),
                updateTargetPrice = UpdateTargetPriceUseCase(repository),
                updateWatchlistOrder = UpdateWatchlistOrderUseCase(repository),
                getAllTrades = GetAllTradesUseCase(repository),
                getTradesForTicker = GetTradesForTickerUseCase(repository),
                logTrade = LogTradeUseCase(repository),
                updateTrade = UpdateTradeUseCase(repository),
                deleteLoggedTrade = DeleteLoggedTradeUseCase(repository),
                getCalculatorSnapshot = GetCalculatorSnapshotUseCase(repository),
                getCalculatorSnapshotFlow = GetCalculatorSnapshotFlowUseCase(repository),
                updateCalculatorSnapshot = UpdateCalculatorSnapshotUseCase(repository)
            )
        }
    }
}
