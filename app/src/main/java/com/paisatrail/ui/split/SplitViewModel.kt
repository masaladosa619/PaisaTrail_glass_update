package com.paisatrail.ui.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisatrail.data.local.FriendEntity
import com.paisatrail.data.prefs.AppSettings
import com.paisatrail.data.prefs.SettingsStore
import com.paisatrail.data.repo.SplitRepository
import com.paisatrail.data.repo.SplitSnapshot
import com.paisatrail.domain.split.ShareInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by the Split tab, the add-expense screen and the settle-up screen. */
@HiltViewModel
class SplitViewModel @Inject constructor(
    private val repository: SplitRepository,
    settings: SettingsStore
) : ViewModel() {

    val snapshot: StateFlow<SplitSnapshot> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitSnapshot())

    val profile: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun addFriend(name: String, upiId: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addFriend(name, upiId) }
    }

    fun setFriendUpi(friend: FriendEntity, upiId: String) {
        viewModelScope.launch { repository.updateFriend(friend.copy(upiId = upiId.trim().ifEmpty { null })) }
    }

    fun deleteFriend(id: Long) {
        viewModelScope.launch { repository.deleteFriend(id) }
    }

    fun createGroup(name: String, friendIds: List<Long>) {
        if (name.isBlank() || friendIds.isEmpty()) return
        viewModelScope.launch { repository.createGroup(name, friendIds) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { repository.deleteGroup(id) }
    }

    fun addExpense(description: String, amountPaise: Long, paidById: Long, shares: List<ShareInput>, groupId: Long?) {
        viewModelScope.launch { repository.addExpense(description, amountPaise, paidById, shares, groupId) }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { repository.deleteExpense(id) }
    }

    fun settle(friendId: Long, amountPaise: Long, friendPaidYou: Boolean) {
        if (amountPaise <= 0) return
        viewModelScope.launch { repository.settle(friendId, amountPaise, friendPaidYou) }
    }

    fun deleteSettlement(id: Long) {
        viewModelScope.launch { repository.deleteSettlement(id) }
    }
}
