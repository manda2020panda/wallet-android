/*
 * Copyright 2013 Megion Research and Development GmbH
 *
 *  Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 *  This license governs use of the accompanying software. If you use the software, you accept this license.
 *  If you do not accept the license, do not use the software.
 *
 *  1. Definitions
 *  The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 *  "You" means the licensee of the software.
 *  "Your company" means the company you worked for when you downloaded the software.
 *  "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 *  of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 *  software, and specifically excludes the right to distribute the software outside of your company.
 *  "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 *  under this license.
 *
 *  2. Grant of Rights
 *  (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 *  worldwide, royalty-free copyright license to reproduce the software for reference use.
 *  (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 *  worldwide, royalty-free patent license under licensed patents for reference use.
 *
 *  3. Limitations
 *  (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 *  (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 *  (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 *  (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 *  guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 *  change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 *  fitness for a particular purpose and non-infringement.
 *
 */

package com.mrd.mbw;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import com.mrd.bitlib.util.CoinUtil;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mrd.mbw.api.AndroidApiCache;
import com.mrd.mbw.api.AndroidAsyncApi;
import com.mrd.mbw.api.ApiCache;

public class MbwManager {
   private static volatile MbwManager _instance = null;

   public static synchronized MbwManager getInstance(Application application) {
      if (_instance == null) {
         _instance = new MbwManager(application);
      }
      return _instance;
   }

   private NetworkConnectionWatcher _connectionWatcher;
   private Context _applicationContext;
   private int _displayWidth;
   private int _displayHeight;
   private ApiCache _cache;
   private AndroidAsyncApi _asyncApi;
   private RecordManager _recordManager;
   private AddressBookManager _addressBookManager;
   private HintManager _hintManager;
   private ExternalStorageManager _externalStorageManager;
   private final String _btcValueFormatString;
   private String _pin;
   private Denomination _bitcoinDenomination;
   private String _fiatCurrency;
   private boolean _showHints;

   private MbwManager(Application application) {
      _applicationContext = application.getApplicationContext();
      _connectionWatcher = new NetworkConnectionWatcher(_applicationContext);
      _cache = new AndroidApiCache(_applicationContext);
      _asyncApi = new AndroidAsyncApi(Constants.bccapi, _cache);

      _btcValueFormatString = _applicationContext.getResources().getString(R.string.btc_value_string);

      // Preferences
      SharedPreferences preferences = _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME,
            Activity.MODE_PRIVATE);

      _pin = preferences.getString(Constants.PIN_SETTING, "");
      _fiatCurrency = preferences.getString(Constants.FIAT_CURRENCY_SETTING, Constants.DEFAULT_CURRENCY);
      _bitcoinDenomination = Denomination.fromString(preferences.getString(Constants.BITCOIN_DENOMINATION_SETTING,
            Constants.DEFAULT_BITCOIN_DENOMINATION));
      _showHints = preferences.getBoolean(Constants.SHOW_HINTS_SETTING, true);

      // Get the display metrics of this device
      DisplayMetrics dm = new DisplayMetrics();
      WindowManager windowManager = (WindowManager) _applicationContext.getSystemService(Context.WINDOW_SERVICE);
      windowManager.getDefaultDisplay().getMetrics(dm);
      _displayWidth = dm.widthPixels;
      _displayHeight = dm.heightPixels;

      _recordManager = new RecordManager(_applicationContext);
      _addressBookManager = new AddressBookManager(application);
      _hintManager = new HintManager(this, _applicationContext);
      _externalStorageManager = new ExternalStorageManager();

   }

   public ApiCache getCache() {
      return _cache;
   }

   public int getDisplayWidth() {
      return _displayWidth;
   }

   public NetworkConnectionWatcher getNetworkConnectionWatcher() {
      return _connectionWatcher;
   }

   public int getDisplayHeight() {
      return _displayHeight;
   }

   public String getFiatCurrency() {
      return _fiatCurrency;
   }

   public void setFiatCurrency(String currency) {
      _fiatCurrency = currency;
      SharedPreferences.Editor editor = _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME,
            Activity.MODE_PRIVATE).edit();
      editor.putString(Constants.FIAT_CURRENCY_SETTING, _fiatCurrency);
      editor.commit();
   }

   public AndroidAsyncApi getAsyncApi() {
      return _asyncApi;
   }

   public RecordManager getRecordManager() {
      return _recordManager;
   }

   public AddressBookManager getAddressBookManager() {
      return _addressBookManager;
   }

   public HintManager getHintManager() {
      return _hintManager;
   }

   public ExternalStorageManager getExternalStorageManager() {
      return _externalStorageManager;
   }

   public boolean isPinProtected() {
      return getPin().length() > 0;
   }

   private String getPin() {
      return _pin;
   }

   public void setPin(String pin) {
      _pin = pin;
      SharedPreferences.Editor editor = _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME,
            Activity.MODE_PRIVATE).edit();
      editor.putString(Constants.PIN_SETTING, _pin);
      editor.commit();
   }

   public void runPinProtectedFunction(final Context context, final Runnable fun) {
      if (isPinProtected()) {
         Dialog d = new PinDialog(context, true, new PinDialog.OnPinEntered() {

            @Override
            public void pinEntered(PinDialog dialog, String pin) {
               if (pin.equals(getPin())) {
                  dialog.dismiss();
                  fun.run();
               } else {
                  Toast.makeText(context, R.string.pin_invalid_pin, Toast.LENGTH_LONG).show();
                  vibrate(500);
                  dialog.dismiss();
               }
            }
         });
         d.setTitle(R.string.pin_enter_pin);
         d.show();
      } else {
         fun.run();
      }
   }

   public void vibrate(int milliseconds) {
      Vibrator v = (Vibrator) _applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(milliseconds);
   }

   public CoinUtil.Denomination getBitcoinDenomination() {
      return _bitcoinDenomination;
   }

   public void setBitcoinDenomination(CoinUtil.Denomination denomination) {
      _bitcoinDenomination = denomination;
      SharedPreferences.Editor editor = _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME,
            Activity.MODE_PRIVATE).edit();
      editor.putString(Constants.BITCOIN_DENOMINATION_SETTING, _bitcoinDenomination.toString());
      editor.commit();
   }

   public String getBtcValueString(long satoshis) {
      return getBtcValueString(satoshis, _btcValueFormatString);
   }

   public String getBtcValueString(long satoshis, String formatString) {
      Denomination d = getBitcoinDenomination();
      String valueString = CoinUtil.valueString(satoshis, d);
      return String.format(formatString, valueString, d.getUnicodeName());
   }

   public boolean getShowHints() {
      return _showHints;
   }

   public void setShowHints(boolean show) {
      _showHints = show;
      SharedPreferences.Editor editor = _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME,
            Activity.MODE_PRIVATE).edit();
      editor.putBoolean(Constants.SHOW_HINTS_SETTING, _showHints);
      editor.commit();
   }

}
