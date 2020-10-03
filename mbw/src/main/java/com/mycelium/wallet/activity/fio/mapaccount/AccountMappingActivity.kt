package com.mycelium.wallet.activity.fio.mapaccount

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.activity.fio.mapaccount.viewmodel.FIOMapPubAddressViewModel
import com.mycelium.wapi.wallet.fio.FioAccount
import java.util.*

class AccountMappingActivity : AppCompatActivity(R.layout.activity_account_mapping) {
    private lateinit var viewModel: FIOMapPubAddressViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.run {
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }
        viewModel = ViewModelProviders.of(this).get(FIOMapPubAddressViewModel::class.java)
        val walletManager = MbwManager.getInstance(this.application).getWalletManager(false)
        if (intent?.extras?.containsKey("accountId") == true) {
            val accountId = intent.getSerializableExtra("accountId") as UUID
            val fioAccount = walletManager.getAccount(accountId) as FioAccount
            viewModel.account.value = fioAccount
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean =
            when (item?.itemId) {
                android.R.id.home -> {
                    onBackPressed()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
}
