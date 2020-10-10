package com.mycelium.wallet.activity.send

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.View.*
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wapi.wallet.Address
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.erc20.coins.ERC20Token
import com.mycelium.wapi.wallet.fio.FioModule
import com.mycelium.wapi.wallet.fio.FioName
import com.mycelium.wapi.wallet.fio.GetPubAddressResponse
import fiofoundation.io.fiosdk.isFioAddress
import kotlinx.android.synthetic.main.manual_entry.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
import java.util.*

class ManualAddressEntry : AppCompatActivity() {
    private var coinAddress: Address? = null
    private var fioAddress: String? = null
    private var entered: String? = null
    private lateinit var mbwManager: MbwManager
    private lateinit var fioModule: FioModule
    private lateinit var fioNames: Array<String>
    private lateinit var coinType: CryptoCurrency
    private val fioNameToNbpaMap = mutableMapOf<String, String>()
    private var fioQueryCounter = 0
    private val checkedFioNames = mutableSetOf<String>()
    private val fioNameRegistered = mutableMapOf<String, Boolean>()
    private var noConnection = false
    private lateinit var statusViews: List<View>
    private val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val isForFio = intent.getBooleanExtra(FOR_FIO_REQUEST, false)
        supportActionBar?.run {
            title = getString(if(!isForFio)R.string.enter_recipient_title else R.string.fio_enter_fio_name_title)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manual_entry)
        statusViews = listOf(tvCheckingFioAddress, tvRecipientInvalid, tvRecipientValid,
                tvNoConnection, tvEnterRecipientDescription, tvRecipientHasNoSuchAddress)
        mbwManager = MbwManager.getInstance(this)
        coinType = if (!isForFio) mbwManager.selectedAccount.coinType else Utils.getFIOCoinType()
        fioModule = mbwManager.getWalletManager(false).getModuleById(FioModule.ID) as FioModule
        fioNames = fioModule.getKnownNames().map { "${it.name}@${it.domain}" }.toTypedArray()
        etRecipient.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) = Unit
            override fun beforeTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) = Unit
            override fun afterTextChanged(editable: Editable) {
                updateUI()
            }
        })
        btOk.setOnClickListener { finishOk(coinAddress!!) }
        etRecipient.inputType = InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        etRecipient.hint = if (!isForFio) getString(R.string.enter_recipient_hint, coinType.name) else getString(R.string.fio_name)
        tvEnterRecipientDescription.text = getString(R.string.enter_recipient_description, coinType.name)
        lvKnownFioNames.adapter = ArrayAdapter<String>(this, R.layout.fio_address_item, fioNames)
        lvKnownFioNames.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            etRecipient.setText(parent.adapter.getItem(position) as String)
        }

        // Load saved state
        entered = savedInstanceState?.getString("entered") ?: ""
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean =
            when (item?.itemId) {
                android.R.id.home -> {
                    onBackPressed()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    private fun updateUI() {
        tvRecipientHasNoSuchAddress.text = getString(R.string.recipient_has_no_such_address, etRecipient.text, coinType.symbol)
        val isFio: Boolean = etRecipient.text.toString().isFioAddress()
        coinAddress = coinType.parseAddress(fioNameToNbpaMap[etRecipient.text.toString()])
        if (entered != etRecipient.text.toString()) {
            entered = etRecipient.text.toString()
            fioAddress = if (isFio) {
                tryFio(entered!!)
                entered
            } else {
                coinAddress = coinType.parseAddress(entered!!.trim { it <= ' ' })
                null
            }
        }
        val recipientValid = coinAddress != null
        for (tv in statusViews) { tv.visibility = GONE }
        when {
            entered?.isEmpty() ?: true -> tvEnterRecipientDescription
            recipientValid -> tvRecipientValid
            fioQueryCounter > 0 -> tvCheckingFioAddress
            noConnection && isFio -> tvNoConnection
            fioNameRegistered[entered!!] == true -> tvRecipientHasNoSuchAddress
            else -> tvRecipientInvalid
        }.visibility = VISIBLE
        btOk.isEnabled = recipientValid
        val filteredNames = fioNames.filter {
                    it.startsWith(entered.toString(), true)
                }.toTypedArray()
        if (filteredNames.isEmpty()) {
            llKnownFioNames.visibility = GONE
        } else {
            lvKnownFioNames.adapter = ArrayAdapter<String>(this@ManualAddressEntry,
                    R.layout.fio_address_item, filteredNames)
            llKnownFioNames.visibility = VISIBLE
        }
    }

    private fun finishOk(address: Address) {
        val result = Intent().apply {
            putExtra(ADDRESS_RESULT_NAME, address)
            putExtra(ADDRESS_RESULT_FIO, fioAddress)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    /**
     * Query FIO for the given fio address
     */
    private fun tryFio(address: String) {
        if (fioNameToNbpaMap.contains(address) || checkedFioNames.contains(address)) {
            // we have the result cached already
            return
        }
        fioQueryCounter+=2 // we query the server twice
        checkedFioNames.add(address)
        updateUI()
        // TODO: 10/6/20 Use: val result = FioTransactionHistoryService.getPubkeyByFioAddress()
        //       It probably needs changes to preserve the error handling.
        val tokenCode = coinType.symbol.toUpperCase(Locale.US)
        val chainCode = if (coinType is ERC20Token) "ETH" else tokenCode
        queryAddress(address, chainCode, tokenCode)
        queryAddressAvailability(address)
    }

    private fun queryAddress(address: String, chainCode: String, tokenCode: String) {
        val requestBody = """{"fio_address":"$address","chain_code":"$chainCode","token_code":"$tokenCode"}"""
        val request = Request.Builder()
                .url("${Utils.getFIOCoinType().url}chain/get_pub_address")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onResponse(call: Call, response: Response) {
                noConnection = false
                val reply = response.body()!!.string()
                val result = mapper.readValue(reply, GetPubAddressResponse::class.java)
                result.publicAddress?.let { npbaString ->
                    fioModule.addKnownName(FioName(address))
                    fioNameToNbpaMap[address] = npbaString
                }
                fioQueryCounter--
                runOnUiThread { updateUI() }
            }

            override fun onFailure(call: Call, e: IOException) {
                checkedFioNames.remove(address)
                noConnection = true
                fioQueryCounter--
                runOnUiThread { updateUI() }
            }
        })
    }

    private fun queryAddressAvailability(address: String) {
        val requestBody = """{"fio_name":"$address"}"""
        val request = Request.Builder()
                .url("${Utils.getFIOCoinType().url}chain/avail_check")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                .build()
        client.newCall(request).enqueue(object: Callback {
            override fun onResponse(call: Call, response: Response) {
                fioQueryCounter--
                noConnection = false
                val reply = response.body()!!.string()
                fioNameRegistered[address] = reply.contains("1") //.contains(""""is_registered":1""")
                runOnUiThread { updateUI() }
            }

            override fun onFailure(call: Call, e: IOException) {
                fioQueryCounter--
                noConnection = true
                runOnUiThread { updateUI() }
            }
        })
    }

    override fun onResume() {
        etRecipient.setText(entered)
        super.onResume()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putSerializable("entered", etRecipient.text.toString())
    }

    companion object {
        const val ADDRESS_RESULT_NAME = "address"
        const val ADDRESS_RESULT_FIO = "fioName"
        const val FOR_FIO_REQUEST = "IS_FOR_FIO_REQUEST"
    }
}
