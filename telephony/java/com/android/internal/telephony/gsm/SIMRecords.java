/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.gsm;

import android.app.AlarmManager;
import android.content.Context;
import android.os.AsyncResult;
import android.os.RegistrantList;
import android.os.Registrant;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import java.util.ArrayList;
import android.telephony.gsm.GsmCellLocation;
import android.os.SystemProperties;
import android.telephony.gsm.SmsManager;
import java.util.PriorityQueue;
import static com.android.internal.telephony.TelephonyProperties.*;
import com.android.internal.telephony.SimCard;

/**
 * {@hide}
 */
public final class SIMRecords extends Handler implements SimConstants
{
    static final String LOG_TAG = "GSM";
    static final String EONS_TAG = "EONS";
    static final String CSP_TAG = "CSP SIM Records";
    static final String SIMSMS_TAG = "SIM SMS";
    private static final boolean CRASH_RIL = false;

    private static final boolean DBG = true;
    private final PriorityQueue<Integer> indexQueue = new PriorityQueue<Integer>();

    //***** Instance Variables

    GSMPhone phone;
    RegistrantList recordsLoadedRegistrants = new RegistrantList();

    int recordsToLoad;  // number of pending load requests

    AdnRecordCache adnCache;

    VoiceMailConstants mVmConfig;
    SpnOverride mSpnOverride;
    
    //***** Cached SIM State; cleared on channel close

    boolean recordsRequested = false; // true if we've made requests for the sim records

    String imsi;
    String iccid;
    String msisdn = null;  // My mobile number
    String msisdnTag = null;
    String voiceMailNum = null;
    String voiceMailTag = null;
    String newVoiceMailNum = null;
    String newVoiceMailTag = null;
    boolean isVoiceMailFixed = false;
    int countVoiceMessages = 0;
    boolean oplDataPresent;
    int     oplDataLac1;
    int     oplDataLac2;
    short   oplDataPnnNum;
    boolean pnnDataPresent;
    String  pnnDataLongName;
    String  pnnDataShortName;
    int     sstPlmnOplValue;
    ArrayList oplCache;
    ArrayList pnnCache;
    boolean callForwardingEnabled;
    int mncLength = 0;   // 0 is used to indicate that the value
                         // is not initialized
    int mailboxIndex = 0; // 0 is no mailbox dailing number associated

    /**
     * Sates only used by getSpnFsm FSM
     */
    private Get_Spn_Fsm_State spnState;

    /** CPHS service information (See CPHS 4.2 B.3.1.1)
     *  It will be set in onSimReady if reading GET_CPHS_INFO successfully
     *  mCphsInfo[0] is CPHS Phase
     *  mCphsInfo[1] and mCphsInfo[2] is CPHS Service Table
     */
    private byte[] mCphsInfo = null;
    private byte[] cspCphsInfo = null;
    int cspPlmn = 1;

    byte[] efMWIS = null;
    byte[] efCPHS_MWI =null;
    byte[] mEfCff = null;
    byte[] mEfCfis = null;


    String spn;
    int spnDisplayCondition;
    // Numeric network codes listed in TS 51.011 EF[SPDI]
    ArrayList<String> spdiNetworks = null;

    String pnnHomeName = null;

    //***** Constants

    // Bitmasks for SPN display rules.
    static final int SPN_RULE_SHOW_SPN  = 0x01;
    static final int SPN_RULE_SHOW_PLMN = 0x02;
    static final int EONS_ALG = 0x01;

    // From TS 51.011 EF[SPDI] section
    static final int TAG_SPDI_PLMN_LIST = 0x80;

    // Full Name IEI from TS 24.008
    static final int TAG_FULL_NETWORK_NAME = 0x43;

    // Short Name IEI from TS 24.008
    static final int TAG_SHORT_NETWORK_NAME = 0x45;

    // active CFF from CPHS 4.2 B.4.5
    static final int CFF_UNCONDITIONAL_ACTIVE = 0x0a;
    static final int CFF_UNCONDITIONAL_DEACTIVE = 0x05;
    static final int CFF_LINE1_MASK = 0x0f;
    static final int CFF_LINE1_RESET = 0xf0;

    // CPHS Service Table (See CPHS 4.2 B.3.1)
    private static final int CPHS_SST_MBN_MASK = 0x30;
    private static final int CPHS_SST_MBN_ENABLED = 0x30;

    //***** Event Constants

    private static final int EVENT_SIM_READY = 1;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 2;
    private static final int EVENT_GET_IMSI_DONE = 3;
    private static final int EVENT_GET_ICCID_DONE = 4;
    private static final int EVENT_GET_MBI_DONE = 5;
    private static final int EVENT_GET_MBDN_DONE = 6;
    private static final int EVENT_GET_MWIS_DONE = 7;
    private static final int EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE = 8;
    private static final int EVENT_GET_AD_DONE = 9; // Admin data on SIM
    private static final int EVENT_GET_MSISDN_DONE = 10;
    private static final int EVENT_GET_CPHS_MAILBOX_DONE = 11;
    private static final int EVENT_GET_SPN_DONE = 12;
    private static final int EVENT_GET_SPDI_DONE = 13;
    private static final int EVENT_UPDATE_DONE = 14;
    private static final int EVENT_GET_PNN_DONE = 15;
    private static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    private static final int EVENT_SET_MBDN_DONE = 20;
    private static final int EVENT_SMS_ON_SIM = 21;
    private static final int EVENT_GET_SMS_DONE = 22;
    private static final int EVENT_GET_CFF_DONE = 24;
    private static final int EVENT_SET_CPHS_MAILBOX_DONE = 25;
    private static final int EVENT_GET_INFO_CPHS_DONE = 26;
    private static final int EVENT_SET_MSISDN_DONE = 30;
    private static final int EVENT_SIM_REFRESH = 31;
    private static final int EVENT_GET_CFIS_DONE = 32;
    private static final int EVENT_GET_ALL_OPL_RECORDS_DONE = 33;
    private static final int EVENT_GET_CSP_CPHS_DONE = 34;
    private static final int EVENT_AUTO_SELECT_DONE = 300;
    private static final int EVENT_GET_ALL_PNN_RECORDS_DONE = 35;

    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";

    //EONS constants
    private static int NOT_INITIALIZED  = -1;
    private static int EONS_DISABLED    = 0;
    private static int PNN_OPL_ENABLED  = 1;
    private static int ONLY_PNN_ENABLED = 2;

    //***** Constructor

    SIMRecords(GSMPhone phone)
    {
        this.phone = phone;

        adnCache = new AdnRecordCache(phone);

        mVmConfig = new VoiceMailConstants();
        mSpnOverride = new SpnOverride();

        recordsRequested = false;  // No load request is made till SIM ready

        // recordsToLoad is set to 0 because no requests are made yet
        recordsToLoad = 0;


        phone.mCM.registerForSIMReady(this, EVENT_SIM_READY, null);
        phone.mCM.registerForOffOrNotAvailable(
                        this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        phone.mCM.setOnSmsOnSim(this, EVENT_SMS_ON_SIM, null);
        phone.mCM.setOnSimRefresh(this, EVENT_SIM_REFRESH, null);


        // Start off by setting empty state
        onRadioOffOrNotAvailable();        

    }

    AdnRecordCache getAdnCache() {
        return adnCache;
    }

    private void onRadioOffOrNotAvailable()
    {
        imsi = null;
        msisdn = null;
        voiceMailNum = null;
        countVoiceMessages = 0;
        mncLength = 0;
        iccid = null;
        spn = null;
        // -1 means no EF_SPN found; treat accordingly.
        spnDisplayCondition = -1;
        efMWIS = null;
        efCPHS_MWI = null; 
        spdiNetworks = null;
        pnnHomeName = null;
        oplDataPresent = false;
        pnnDataPresent = false;
        sstPlmnOplValue = NOT_INITIALIZED;
        oplCache = null;
        pnnCache = null;


        adnCache.reset();

        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_NUMERIC, null);
        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, null);
        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ISO_COUNTRY, null);

        // recordsRequested is set to false indicating that the SIM
        // read requests made so far are not valid. This is set to
        // true only when fresh set of read requests are made.
        recordsRequested = false;
    }


    //***** Public Methods
    public void registerForRecordsLoaded(Handler h, int what, Object obj)
    {
        Registrant r = new Registrant(h, what, obj);
        recordsLoadedRegistrants.add(r);

        if (recordsToLoad == 0 && recordsRequested == true) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    /** Returns null if SIM is not yet ready */
    public String getIMSI()
    {
        return imsi;
    }

    public String getMsisdnNumber()
    {
        return msisdn;
    }

    /**
     * Set subscriber number to SIM record
     *
     * The subscriber number is stored in EF_MSISDN (TS 51.011)
     *
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param alphaTag alpha-tagging of the dailing nubmer (up to 10 characters)
     * @param number dailing nubmer (up to 20 digits)
     *        if the number starts with '+', then set to international TOA
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void setMsisdnNumber(String alphaTag, String number,
            Message onComplete) {

        msisdn = number;
        msisdnTag = alphaTag;

        if(DBG) log("Set MSISDN: " + msisdnTag +" " + msisdn);


        AdnRecord adn = new AdnRecord(msisdnTag, msisdn);

        new AdnRecordLoader(phone).updateEF(adn, EF_MSISDN, EF_EXT1, 1, null,
                obtainMessage(EVENT_SET_MSISDN_DONE, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return msisdnTag;
    }

    public String getVoiceMailNumber()
    {
        return voiceMailNum;
    }

    /**
     * Return Service Provider Name stored in SIM
     * @return null if SIM is not yet ready
     */
    String getServiceProviderName()
    {
        return spn;
    }
   /**
     * Return pnn Long Name stored in SIM if available
     * @return null if SIM is not yet ready or pnn is absent/invalid
     */
    String getPnnLongName()
    {
        if(pnnDataPresent) {
           return pnnDataLongName;
        }
        else {
           return null;
        }
    }

    /**
     * Checks if adapt property is set.
     * @return true = set, flase = not set.
     */
    boolean adaptPropSet()
    {
        boolean adapt_set = false;
        String adapt_prop = SystemProperties.get("persist.cust.tel.adapt");
        if ((adapt_prop != null) && (adapt_prop.length() != 0)) {
            try{
                if (Integer.valueOf(adapt_prop) == 1) {
                    adapt_set = true;
               }
            } catch(Exception e){
                Log.e(EONS_TAG,"Exception on reading persist.cust.tel.adapt " + e);
            }
        }
        return adapt_set;
    }

    /**
     * Return which ONS Display Algorithm to be used
     * @return default alg if SIM is not yet ready
     */
    int getOnsAlg()
    {
        int ons_alg = 0;
        String eons_prop   = SystemProperties.get("persist.cust.tel.eons");

        //EONS algorithm is disabled if PNN service is not activated.
        if (sstPlmnOplValue == EONS_DISABLED || sstPlmnOplValue == NOT_INITIALIZED) {
            return 0;
        }
        //persist.cust.tel.adapt is super flag, if this is set then EONS
        //should be enabled irrespective of the value of
        //persist.cust.tel.eons prop. Otherwise EONS should be enabled if
        //persist.cust.tel.eons is set.
        if (adaptPropSet()) {
            ons_alg = EONS_ALG;
        }
        else if ((eons_prop != null) && (eons_prop.length() != 0)) {
            try{
                if (Integer.valueOf(eons_prop) == 1) {
                    ons_alg = EONS_ALG;
               }
            } catch(Exception e){
                Log.e(EONS_TAG,"Exception on reading persist.cust.tel.eons " + e);
            }
        }
        return ons_alg;
    }


    /**
     * Update EONS data from EF_OPL/EF_PNN files when LAC/Reg PLMN changes
     */
    boolean updateSimRecords(int flag)
    {
        //If both PNN and OPL services are enabled, a match should be found
        //in OPL file and corresponding record in the PNN file should be processed.
        if (sstPlmnOplValue == PNN_OPL_ENABLED) {
            displayEonsName(flag);
        }
        //If PNN service is enabled and OPL service is disabled, process first
        //record of PNN file if registered PLMN is HPLMN.
        else if (sstPlmnOplValue == ONLY_PNN_ENABLED) {
            fetchPnnFirstRecord(flag);
        }
        else {
            Log.e(EONS_TAG,"Invalid sstPlmnOplValue "+sstPlmnOplValue);
        }
        return true;
    }

    /** Return the csp plmn status from EF_CSP if present*/
    public int getCspPlmn() {
        return cspPlmn;
    }

    /**
     * Set voice mail number to SIM record
     *
     * The voice mail number can be stored either in EF_MBDN (TS 51.011) or
     * EF_MAILBOX_CPHS (CPHS 4.2)
     *
     * If EF_MBDN is available, store the voice mail number to EF_MBDN
     * 
     * If EF_MAILBOX_CPHS is enabled, store the voice mail number to EF_CHPS
     *
     * So the voice mail number will be stored in both EFs if both are available
     *
     * Return error only if both EF_MBDN and EF_MAILBOX_CPHS fail.
     *
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param alphaTag alpha-tagging of the dailing nubmer (upto 10 characters)
     * @param voiceNumber dailing nubmer (upto 20 digits)
     *        if the number is start with '+', then set to international TOA
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void setVoiceMailNumber(String alphaTag, String voiceNumber,
            Message onComplete) {
        if (isVoiceMailFixed) {
            AsyncResult.forMessage((onComplete)).exception =
                    new SimVmFixedException("Voicemail number is fixed by operator");
            onComplete.sendToTarget();
            return;
        }

        newVoiceMailNum = voiceNumber;
        newVoiceMailTag = alphaTag;

        AdnRecord adn = new AdnRecord(newVoiceMailTag, newVoiceMailNum);

        if (mailboxIndex != 0 && mailboxIndex != 0xff) {

            new AdnRecordLoader(phone).updateEF(adn, EF_MBDN, EF_EXT6,
                    mailboxIndex, null,
                    obtainMessage(EVENT_SET_MBDN_DONE, onComplete));

        } else if (isCphsMailboxEnabled()) {

            new AdnRecordLoader(phone).updateEF(adn, EF_MAILBOX_CPHS,
                    EF_EXT1, 1, null,
                    obtainMessage(EVENT_SET_CPHS_MAILBOX_DONE, onComplete));

        }else {
            AsyncResult.forMessage((onComplete)).exception =
                    new SimVmNotSupportedException("Update SIM voice mailbox error");
            onComplete.sendToTarget();
        }
    }

    public String getVoiceMailAlphaTag()
    {
        return voiceMailTag;
    }

    /**
     * Sets the SIM voice message waiting indicator records
     * @param line GSM Subscriber Profile Number, one-based. Only '1' is supported
     * @param countWaiting The number of messages waiting, if known. Use
     *                     -1 to indicate that an unknown number of 
     *                      messages are waiting
     */
    public void
    setVoiceMessageWaiting(int line, int countWaiting)
    {
        if (line != 1) {
            // only profile 1 is supported
            return;
        }

        // range check
        if (countWaiting < 0) {
            countWaiting = -1;
        } else if (countWaiting > 0xff) {
            // TS 23.040 9.2.3.24.2
            // "The value 255 shall be taken to mean 255 or greater"
            countWaiting = 0xff;
        }

        countVoiceMessages = countWaiting;

        phone.notifyMessageWaitingIndicator();

        try {
            if (efMWIS != null) {
                // TS 51.011 10.3.45

                // lsb of byte 0 is 'voicemail' status
                efMWIS[0] = (byte)((efMWIS[0] & 0xfe) 
                                    | (countVoiceMessages == 0 ? 0 : 1));

                // byte 1 is the number of voice messages waiting
                if (countWaiting < 0) {
                    // The spec does not define what this should be
                    // if we don't know the count
                    efMWIS[1] = 0;
                } else {
                    efMWIS[1] = (byte) countWaiting;
                }

                phone.mSIMFileHandler.updateEFLinearFixed(
                    EF_MWIS, 1, efMWIS, null,
                    obtainMessage (EVENT_UPDATE_DONE, EF_MWIS));
            } 

            if (efCPHS_MWI != null) {
                    // Refer CPHS4_2.WW6 B4.2.3
                efCPHS_MWI[0] = (byte)((efCPHS_MWI[0] & 0xf0) 
                            | (countVoiceMessages == 0 ? 0x5 : 0xa));

                phone.mSIMFileHandler.updateEFTransparent(
                    EF_VOICE_MAIL_INDICATOR_CPHS, efCPHS_MWI,
                    obtainMessage (EVENT_UPDATE_DONE, EF_VOICE_MAIL_INDICATOR_CPHS));
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            Log.w(LOG_TAG,
                "Error saving voice mail state to SIM. Probably malformed SIM record", ex);
        }
    }

    /** @return  true if there are messages waiting, false otherwise. */
    public boolean getVoiceMessageWaiting()
    {
        return countVoiceMessages != 0;
    }

    /**
     * Returns number of voice messages waiting, if available
     * If not available (eg, on an older CPHS SIM) -1 is returned if 
     * getVoiceMessageWaiting() is true
     */
    public int getCountVoiceMessages()
    {
        return countVoiceMessages;
    }

    public boolean getVoiceCallForwardingFlag() {
        return callForwardingEnabled;
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable) {

        if (line != 1) return; // only line 1 is supported

        callForwardingEnabled = enable;

        phone.notifyCallForwardingIndicator();

        try {
            if (mEfCfis != null) {
                // lsb is of byte 1 is voice status
                if (enable) {
                    mEfCfis[1] |= 1;
                } else {
                    mEfCfis[1] &= 0xfe;
                }

                // TODO: Should really update other fields in EF_CFIS, eg,
                // dialing number.  We don't read or use it right now.

                phone.mSIMFileHandler.updateEFLinearFixed(
                        EF_CFIS, 1, mEfCfis, null,
                        obtainMessage (EVENT_UPDATE_DONE, EF_CFIS));
            }

            if (mEfCff != null) {
                if (enable) {
                    mEfCff[0] = (byte) ((mEfCff[0] & CFF_LINE1_RESET)
                            | CFF_UNCONDITIONAL_ACTIVE);
                } else {
                    mEfCff[0] = (byte) ((mEfCff[0] & CFF_LINE1_RESET)
                            | CFF_UNCONDITIONAL_DEACTIVE);
                }

                phone.mSIMFileHandler.updateEFTransparent(
                        EF_CFF_CPHS, mEfCff,
                        obtainMessage (EVENT_UPDATE_DONE, EF_CFF_CPHS));
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            Log.w(LOG_TAG,
                    "Error saving call fowarding flag to SIM. "
                            + "Probably malformed SIM record", ex);

        }
    }

    /**
     * Called by STK Service when REFRESH is received.
     * @param fileChanged indicates whether any files changed
     * @param fileList if non-null, a list of EF files that changed
     */
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            // A future optimization would be to inspect fileList and
            // only reload those files that we care about.  For now,
            // just re-fetch all SIM records that we cache.
            fetchSimRecords();
        }
    }

    /** Returns the 5 or 6 digit MCC/MNC of the operator that
     *  provided the SIM card. Returns null of SIM is not yet ready
     */
    String getSIMOperatorNumeric()
    {
        if (imsi == null) {
            return null;
        }

        if (mncLength != 0) {
            // Length = length of MCC + length of MNC
            // length of mcc = 3 (TS 23.003 Section 2.2)
            return imsi.substring(0, 3 + mncLength);
        }

        // Guess the MNC length based on the MCC if we don't
        // have a valid value in ef[ad]

        int mcc;

        mcc = Integer.parseInt(imsi.substring(0,3));

        return imsi.substring(0, 3 + MccTable.smallestDigitsMccForMnc(mcc));
    }

    boolean getRecordsLoaded()
    {
        if (recordsToLoad == 0 && recordsRequested == true) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * If the timezone is not already set, set it based on the MCC of the SIM.
     * @param mcc Mobile Country Code of the SIM
     */
    private void setTimezoneFromMccIfNeeded(int mcc) {
        String timezone = SystemProperties.get(TIMEZONE_PROPERTY);
        if (timezone == null || timezone.length() == 0) {
            String zoneId = MccTable.defaultTimeZoneForMcc(mcc);
            
            if (zoneId != null && zoneId.length() > 0) {
                // Set time zone based on MCC
                AlarmManager alarm =
                    (AlarmManager) phone.getContext().getSystemService(Context.ALARM_SERVICE);
                alarm.setTimeZone(zoneId);
            }
        }
    }

    /**
     * If the locale is not already set, set it based on the MCC of the SIM.
     * @param mcc Mobile Country Code of the SIM
     */
    private void setLocaleFromMccIfNeeded(int mcc) {
        String language = MccTable.defaultLanguageForMcc(mcc);
        String country = MccTable.countryCodeForMcc(mcc);

        phone.setSystemLocale(language, country);
    }

    //***** Overridden from Handler
    public void handleMessage(Message msg)
    {
        AsyncResult ar;
        AdnRecord adn;

        byte data[];
        byte length;

        boolean isRecordLoadResponse = false;

        try { switch (msg.what) {
            case EVENT_SIM_READY:
                onSimReady();
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
            break;  

            /* IO events */
            case EVENT_GET_IMSI_DONE:
                isRecordLoadResponse = true;
            
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    Log.e(LOG_TAG, "Exception querying IMSI, Exception:" + ar.exception);
                    break;
                } 
                
                imsi = (String) ar.result;

                // IMSI (MCC+MNC+MSIN) is at least 6 digits, but not more
                // than 15 (and usually 15).
                if (imsi != null && (imsi.length() < 6 || imsi.length() > 15)) {
                    Log.e(LOG_TAG, "invalid IMSI " + imsi);
                    imsi = null;
                }
                
                Log.d(LOG_TAG, "IMSI: " + imsi.substring(0, 6) + "xxxxxxxxx");
                phone.mSimCard.updateImsiConfiguration(imsi);
                phone.mSimCard.broadcastSimStateChangedIntent(
                        SimCard.INTENT_VALUE_SIM_IMSI, null);

                int mcc = Integer.parseInt(imsi.substring(0, 3));
                setTimezoneFromMccIfNeeded(mcc);
                setLocaleFromMccIfNeeded(mcc);
            break;

            case EVENT_GET_MBI_DONE:
                boolean isValidMbdn;
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[]) ar.result;

                isValidMbdn = false;
                if (ar.exception == null) {
                    // Refer TS 51.011 Section 10.3.44 for content details
                    Log.d(LOG_TAG, "EF_MBI: " +
                            SimUtils.bytesToHexString(data));

                    // Voice mail record number stored first
                    mailboxIndex = (int)data[0] & 0xff;

                    // check if dailing numbe id valid
                    if (mailboxIndex != 0 && mailboxIndex != 0xff) {
                        Log.d(LOG_TAG, "Got valid mailbox number for MBDN");
                        isValidMbdn = true;
                    }
                }

                // one more record to load
                recordsToLoad += 1;

                if (isValidMbdn) {
                    // Note: MBDN was not included in NUM_OF_SIM_RECORDS_LOADED
                    new AdnRecordLoader(phone).loadFromEF(EF_MBDN, EF_EXT6,
                            mailboxIndex, obtainMessage(EVENT_GET_MBDN_DONE));
                } else {
                    // If this EF not present, try mailbox as in CPHS standard
                    // CPHS (CPHS4_2.WW6) is a european standard.
                    new AdnRecordLoader(phone).loadFromEF(EF_MAILBOX_CPHS,
                            EF_EXT1, 1,
                            obtainMessage(EVENT_GET_CPHS_MAILBOX_DONE));
                }

                break;
            case EVENT_GET_CPHS_MAILBOX_DONE:
            case EVENT_GET_MBDN_DONE:
                //Resetting the voice mail number and voice mail tag to null
                //as these should be updated from the data read from EF_MBDN.
                //If they are not reset, incase of invalid data/exception these
                //variables are retaining their previous values and are
                //causing certain testcases to fail.
                voiceMailNum = null;
                voiceMailTag = null;
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                
                    Log.d(LOG_TAG, "Invalid or missing EF" 
                        + ((msg.what == EVENT_GET_CPHS_MAILBOX_DONE) ? "[MAILBOX]" : "[MBDN]"));

                    // Bug #645770 fall back to CPHS
                    // FIXME should use SST to decide

                    if (msg.what == EVENT_GET_MBDN_DONE) {
                        //load CPHS on fail... 
                        // FIXME right now, only load line1's CPHS voice mail entry

                        recordsToLoad += 1;
                        new AdnRecordLoader(phone).loadFromEF(
                                EF_MAILBOX_CPHS, EF_EXT1, 1, 
                                obtainMessage(EVENT_GET_CPHS_MAILBOX_DONE));
                    }
                    break;
                }

                adn = (AdnRecord)ar.result;

                Log.d(LOG_TAG, "VM: " + adn + ((msg.what == EVENT_GET_CPHS_MAILBOX_DONE) ? " EF[MAILBOX]" : " EF[MBDN]"));

                if (adn.isEmpty() && msg.what == EVENT_GET_MBDN_DONE) {
                    // Bug #645770 fall back to CPHS
                    // FIXME should use SST to decide
                    // FIXME right now, only load line1's CPHS voice mail entry
                    recordsToLoad += 1;
                    new AdnRecordLoader(phone).loadFromEF(
                            EF_MAILBOX_CPHS, EF_EXT1, 1, 
                            obtainMessage(EVENT_GET_CPHS_MAILBOX_DONE));

                    break;
                }

                voiceMailNum = adn.getNumber();
                voiceMailTag = adn.getAlphaTag();
            break;

            case EVENT_GET_MSISDN_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    Log.d(LOG_TAG, "Invalid or missing EF[MSISDN]");
                    break;
                }

                adn = (AdnRecord)ar.result;

                msisdn = adn.getNumber();
                msisdnTag = adn.getAlphaTag();

                Log.d(LOG_TAG, "MSISDN: " + msisdn);
            break;

            case EVENT_SET_MSISDN_DONE:
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;

                if (ar.userObj != null) {
                    AsyncResult.forMessage(((Message) ar.userObj)).exception
                            = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
                break;

            case EVENT_GET_MWIS_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                Log.d(LOG_TAG, "EF_MWIS: " +
                   SimUtils.bytesToHexString(data));

                efMWIS = data;

                if ((data[0] & 0xff) == 0xff) {
                    Log.d(LOG_TAG, "SIMRecords: Uninitialized record MWIS");
                    break;
                }

                // Refer TS 51.011 Section 10.3.45 for the content description
                boolean voiceMailWaiting = ((data[0] & 0x01) != 0);
                countVoiceMessages = data[1] & 0xff;

                if (voiceMailWaiting && countVoiceMessages == 0) {
                    // Unknown count = -1 
                    countVoiceMessages = -1;
                }

                phone.notifyMessageWaitingIndicator();
            break;

            case EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                efCPHS_MWI = data;

                // Use this data if the EF[MWIS] exists and
                // has been loaded

                if (efMWIS == null) {
                    int indicator = (int)(data[0] & 0xf);

                    // Refer CPHS4_2.WW6 B4.2.3
                    if (indicator == 0xA) {
                        // Unknown count = -1
                        countVoiceMessages = -1;
                    } else if (indicator == 0x5) {
                        countVoiceMessages = 0;
                    }

                    phone.notifyMessageWaitingIndicator();
                }
            break;

            case EVENT_GET_ICCID_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;
                
                if (ar.exception != null) {
                    break;
                }                

                iccid = SimUtils.bcdToString(data, 0, data.length);
            
                Log.d(LOG_TAG, "iccid: " + iccid);

            break;


            case EVENT_GET_AD_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                Log.d(LOG_TAG, "EF_AD: " +
                    SimUtils.bytesToHexString(data));

                if (data.length < 3) {
                    Log.d(LOG_TAG, "SIMRecords: Corrupt AD data on SIM");
                    break;
                }

                if (data.length == 3) {
                    Log.d(LOG_TAG, "SIMRecords: MNC length not present in EF_AD");
                    break;
                }

                mncLength = (int)data[3] & 0xf;

                if (mncLength == 0xf) {
                    // Resetting mncLength to 0 to indicate that it is not
                    // initialised
                    mncLength = 0;

                    Log.d(LOG_TAG, "SIMRecords: MNC length not present in EF_AD");
                    break;
                }

            break;

            case EVENT_GET_SPN_DONE:
                isRecordLoadResponse = true;
                ar = (AsyncResult) msg.obj;
                getSpnFsm(false, ar);
            break;

            case EVENT_GET_CFF_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult) msg.obj;
                data = (byte[]) ar.result;

                if (ar.exception != null) {
                    break;
                }

                Log.d(LOG_TAG, "EF_CFF_CPHS: " +
                        SimUtils.bytesToHexString(data));
                mEfCff = data;

                if (mEfCfis == null) {
                    callForwardingEnabled =
                        ((data[0] & CFF_LINE1_MASK) == CFF_UNCONDITIONAL_ACTIVE);

                    phone.notifyCallForwardingIndicator();
                }
                break;

            case EVENT_GET_SPDI_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                parseEfSpdi(data);                                
            break;

            case EVENT_UPDATE_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    Log.i(LOG_TAG, "SIMRecords update failed", ar.exception);
                }
            break;

            case EVENT_GET_PNN_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;
                if (ar.exception != null) {
                   break;
                }

                SimTlv tlv = new SimTlv(data, 0, data.length);

                for ( ; tlv.isValidObject() ; tlv.nextObject()) {
                   if (tlv.getTag() == TAG_FULL_NETWORK_NAME) {
                      pnnHomeName
                         = SimUtils.networkNameToString(
                               tlv.getData(), 0, tlv.getData().length);
                      break;

                   }
                }

            break;

            case EVENT_GET_ALL_SMS_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                if (ar.exception != null)
                    break;

                handleSmses((ArrayList) ar.result);
                break;

            case EVENT_MARK_SMS_READ_DONE:
                Log.i("ENF", "marked read: sms " + msg.arg1);
                break;


            case EVENT_SMS_ON_SIM:
                isRecordLoadResponse = false;

                ar = (AsyncResult)msg.obj;

                int[] index = (int[])ar.result;

                if (ar.exception != null || index.length != 1) {
                    Log.e(LOG_TAG, "[SIMRecords] Error on SMS_ON_SIM with exp "
                            + ar.exception + " length " + index.length);
                } else {
                    try {
                      indexQueue.add(index[0]);
                      Log.i(SIMSMS_TAG, "Saved index=" + index[0]+" in Queue");
                    } catch(Exception e) {
                      Log.e(SIMSMS_TAG, "Eception in adding element to QUEUE "+e);
                    }
                    Log.d(LOG_TAG, "READ EF_SMS RECORD index=" + index[0]);
                    phone.mSIMFileHandler.loadEFLinearFixed(EF_SMS,index[0],obtainMessage(EVENT_GET_SMS_DONE));
                }
                break;

            case EVENT_GET_SMS_DONE:
                int pInd = 0;
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    handleSms((byte[])ar.result);
                } else {
                    Log.e(LOG_TAG, "[SIMRecords] Error on GET_SMS with exp "
                            + ar.exception);
                }
                try {
                  pInd = indexQueue.remove();
                  Log.i(SIMSMS_TAG, "Removed index "+pInd);
                } catch (Exception e) {
                  Log.e(SIMSMS_TAG, "Eception in removing element from QUEUE "+e);
                }
                break;
            case EVENT_GET_SST_DONE:
                sstPlmnOplValue = EONS_DISABLED;
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                Log.i(EONS_TAG, "SST: " + SimUtils.bytesToHexString(data));
                handleSstData(data);
            break;

            case EVENT_GET_INFO_CPHS_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mCphsInfo = (byte[])ar.result;

                if (DBG) log("iCPHS: " + SimUtils.bytesToHexString(mCphsInfo));
            break;

            case EVENT_GET_CSP_CPHS_DONE:
                Log.i(CSP_TAG,"Got Response for GET CSP");
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    Log.e(CSP_TAG,"Exception " + ar.exception);
                    break;
                }

                cspCphsInfo = (byte[])ar.result;

                Log.i(CSP_TAG,SimUtils.bytesToHexString(cspCphsInfo));
                processEFCspData();
            break;

            case EVENT_AUTO_SELECT_DONE:
                Log.i(CSP_TAG,"Got Response for Automatic network selection");
                isRecordLoadResponse = false;

                ar = (AsyncResult) msg.obj;

                if (ar.exception != null) {
                    Log.e(CSP_TAG,"Automatic network selection: failed!");
                } else {
                    Log.i(CSP_TAG,"Automatic network selection: succeeded!");
                }
            break;

            case EVENT_SET_MBDN_DONE:
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;

                if (ar.exception == null) {
                    voiceMailNum = newVoiceMailNum;
                    voiceMailTag = newVoiceMailTag;
                }

                if (isCphsMailboxEnabled()) {
                    adn = new AdnRecord(voiceMailTag, voiceMailNum);
                    Message onCphsCompleted = (Message) ar.userObj;

                    /* write to cphs mailbox whenever it is available but
                    * we only need notify caller once if both updating are
                    * successful.
                    *
                    * so if set_mbdn successful, notify caller here and set
                    * onCphsCompleted to null
                    */
                    if (ar.exception == null && ar.userObj != null) {
                        AsyncResult.forMessage(((Message) ar.userObj)).exception
                                = null;
                        ((Message) ar.userObj).sendToTarget();

                        if (DBG) log("Callback with MBDN successful.");

                        onCphsCompleted = null;
                    }

                    new AdnRecordLoader(phone).
                            updateEF(adn, EF_MAILBOX_CPHS, EF_EXT1, 1, null,
                            obtainMessage(EVENT_SET_CPHS_MAILBOX_DONE,
                                    onCphsCompleted));
                } else {
                    if (ar.userObj != null) {
                        AsyncResult.forMessage(((Message) ar.userObj)).exception
                                = ar.exception;
                        ((Message) ar.userObj).sendToTarget();
                    }
                }
                break;
            case EVENT_SET_CPHS_MAILBOX_DONE:
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;
                if(ar.exception == null) {
                    voiceMailNum = newVoiceMailNum;
                    voiceMailTag = newVoiceMailTag;
                } else {
                    if (DBG) log("Set CPHS MailBox with exception: "
                            + ar.exception);
                }
                if (ar.userObj != null) {
                    if (DBG) log("Callback with CPHS MB successful.");
                    AsyncResult.forMessage(((Message) ar.userObj)).exception
                            = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
                break;
            case EVENT_SIM_REFRESH:
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;
		if (DBG) log("Sim REFRESH with exception: " + ar.exception);
                if (ar.exception == null) {
                    handleSimRefresh((int[])(ar.result));
                }
                break;
            case EVENT_GET_CFIS_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                Log.d(LOG_TAG, "EF_CFIS: " +
                   SimUtils.bytesToHexString(data));

                mEfCfis = data;
                
                // Refer TS 51.011 Section 10.3.46 for the content description
                callForwardingEnabled = ((data[1] & 0x01) != 0);

                phone.notifyCallForwardingIndicator();
                break;

            case EVENT_GET_ALL_OPL_RECORDS_DONE:
                isRecordLoadResponse = true;
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    Log.e(EONS_TAG, "Exception in fetching OPL Records " + ar.exception);
                    oplCache = null;
                    break;
                }
                oplCache = (ArrayList)ar.result;
                displayEonsName(0);
                break;

            case EVENT_GET_ALL_PNN_RECORDS_DONE:
                isRecordLoadResponse = true;
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    Log.e(EONS_TAG, "Exception in fetching PNN Records "+ar.exception);
                    pnnCache = null;
                    break;
                }
                pnnCache = (ArrayList)ar.result;
                displayEonsName(0);
                break;

        }}catch (RuntimeException exc) {
            // I don't want these exceptions to be fatal
            Log.w(LOG_TAG, "Exception parsing SIM record", exc);
        } finally {        
            // Count up record load responses even if they are fails
            if (isRecordLoadResponse) {
                onRecordLoaded();
            }
        }
    }

    private void handleFileUpdate(int efid) {
        switch(efid) {
            case EF_MBDN:
                recordsToLoad++;
                new AdnRecordLoader(phone).loadFromEF(EF_MBDN, EF_EXT6,
                        mailboxIndex, obtainMessage(EVENT_GET_MBDN_DONE));            
                break;
            case EF_MAILBOX_CPHS:
                recordsToLoad++;
                new AdnRecordLoader(phone).loadFromEF(EF_MAILBOX_CPHS, EF_EXT1,
                        1, obtainMessage(EVENT_GET_CPHS_MAILBOX_DONE));          
                break;
            case EF_OPL:
                 if (getOnsAlg() == EONS_ALG) {
                     //Update EF_OPL cache on EF_OPL sim refresh indication.
                     Log.i(EONS_TAG,"SIM Refresh called for EF_OPL");
                     updateOplCache();
                 }
                 else {
                     fetchSimRecords();
                 }
                 break;
            case EF_PNN:
                 if (getOnsAlg() == EONS_ALG) {
                     //Update EF_PNN cache on EF_PNN sim refresh indication.
                     Log.i(EONS_TAG,"SIM Refresh called for EF_PNN");
                     updatePnnCache();
                 }
                 else {
                     fetchSimRecords();
                 }
                 break;
            case EF_CSP_CPHS:
                 if (SystemProperties.getBoolean("persist.cust.tel.adapt",false) ||
                     SystemProperties.getBoolean("persist.cust.tel.efcsp.plmn",false)) {
                     //Update EF_CSP data when there is a sim refresh
                     //indication for EF_CSP_CPHS file.
                     Log.i(CSP_TAG,"SIM Refresh called for EF_CSP_CPHS");
                     phone.mSIMFileHandler.loadEFTransparent(EF_CSP_CPHS,
                           obtainMessage(EVENT_GET_CSP_CPHS_DONE));
                     recordsToLoad++;
                 }
                 break;
            case EF_SST:
                 Log.i(EONS_TAG,"SIM Refresh called for EF_SST");
                 phone.mSIMFileHandler.loadEFTransparent(EF_SST,
                 obtainMessage(EVENT_GET_SST_DONE));
                 recordsToLoad++;
                 break;
            default:
                // For now, fetch all records if this is not a
                // voicemail number.
                // TODO: Handle other cases, instead of fetching all.
                adnCache.reset();
                fetchSimRecords();
                break;
        }
    }

    private void handleSimRefresh(int[] result) { 
        if (result == null || result.length == 0) {
	    if (DBG) log("handleSimRefresh without input");
            return;
        }

        switch ((result[0])) {
            case CommandsInterface.SIM_REFRESH_FILE_UPDATED:
 		if (DBG) log("handleSimRefresh with SIM_REFRESH_FILE_UPDATED");
		// result[1] contains the EFID of the updated file.
                int efid = result[1];
                handleFileUpdate(efid);
                break;
            case CommandsInterface.SIM_REFRESH_INIT:
		if (DBG) log("handleSimRefresh with SIM_REFRESH_INIT");
                // need to reload all files (that we care about)
                adnCache.reset();
                fetchSimRecords();
                break;
            case CommandsInterface.SIM_REFRESH_RESET:
		if (DBG) log("handleSimRefresh with SIM_REFRESH_RESET");
                phone.mCM.setRadioPower(false, null);
                /* Note: no need to call setRadioPower(true).  Assuming the desired
                * radio power state is still ON (as tracked by ServiceStateTracker),
                * ServiceStateTracker will call setRadioPower when it receives the
                * RADIO_STATE_CHANGED notification for the power off.  And if the
                * desired power state has changed in the interim, we don't want to
                * override it with an unconditional power on.
                */
                break;
            default:
                // unknown refresh operation
		if (DBG) log("handleSimRefresh with unknown operation");
                break;
        }
    }

    private void handleSms(byte[] ba)
    {
        if (ba[0] != 0)
            Log.d("ENF", "status : " + ba[0]);

        // 3GPP TS 51.011 v5.0.0 (20011-12)  10.5.3
        // 3 == "received by MS from network; message to be read"
        if (ba[0] == 3) {
            int n = ba.length;

            // Note: Data may include trailing FF's.  That's OK; message
            // should still parse correctly.
            byte[] nba = new byte[n - 1];
            System.arraycopy(ba, 1, nba, 0, n - 1);

            String pdu = SimUtils.bytesToHexString(nba);
            // XXX first line is bogus
            SmsMessage message = SmsMessage.newFromCMT(
                                new String[] { "", pdu });

            phone.mSMS.dispatchMessage(message);
            //GCF Test case AssGCF USIM SMS 8.2.2 checks for
            //status byte(first byte in EF_SMS record)
            //to be 0x01 after the SMS is read from the SIM.
            //So setting the status byte to 0x01 after reading it from SIM.
            markSmsAsRead(SmsManager.STATUS_ON_SIM_READ,nba);
        }
    }

    private void markSmsAsRead(int status,byte[] pdu) {
        byte[] data = new byte[SimConstants.SMS_RECORD_LENGTH];
        int ind = 0;
        if(!indexQueue.isEmpty()) {
           ind = indexQueue.peek();
           Log.i(SIMSMS_TAG, "markSmsAsRead:Updating Record "+ind);
           //Status bits for this record.  See TS 51.011 10.5.3
           data[0] = (byte)(status & 7);

           System.arraycopy(pdu, 0, data, 1, pdu.length);

           // Pad out with 0xFF's.
           for (int j = pdu.length+1; j < SimConstants.SMS_RECORD_LENGTH; j++) {
              data[j] = -1;
           }
           phone.mSIMFileHandler.updateEFLinearFixed( SimConstants.EF_SMS,
                 ind, data, null, null);
        }
    }

    private void handleSmses(ArrayList messages) {
        int count = messages.size();

        for (int i = 0; i < count; i++) {
            byte[] ba = (byte[]) messages.get(i);

            if (ba[0] != 0)
                Log.i("ENF", "status " + i + ": " + ba[0]);

            // 3GPP TS 51.011 v5.0.0 (20011-12)  10.5.3
            // 3 == "received by MS from network; message to be read"

            if (ba[0] == 3) {
                int n = ba.length;

                // Note: Data may include trailing FF's.  That's OK; message
                // should still parse correctly.
                byte[] nba = new byte[n - 1];
                System.arraycopy(ba, 1, nba, 0, n - 1);

                String pdu = SimUtils.bytesToHexString(nba);
                // XXX first line is bogus
                SmsMessage message = SmsMessage.newFromCMT(
                        new String[] { "", pdu });

                phone.mSMS.dispatchMessage(message);

                // 3GPP TS 51.011 v5.0.0 (20011-12)  10.5.3
                // 1 == "received by MS from network; message read"

                ba[0] = 1;

                if (false) { // XXX writing seems to crash RdoServD
                    phone.mSIMFileHandler.updateEFLinearFixed(EF_SMS, i, ba, null,
                                    obtainMessage(EVENT_MARK_SMS_READ_DONE, i));
                }
            }
        }
    }


    //***** Private Methods

    private void onRecordLoaded()
    {
        // One record loaded successfully or failed, In either case
        // we need to update the recordsToLoad count
        recordsToLoad -= 1;

        if (recordsToLoad == 0 && recordsRequested == true) {
            onAllRecordsLoaded();
        } else if (recordsToLoad < 0) {
            Log.e(LOG_TAG, "SIMRecords: recordsToLoad <0, programmer error suspected");
            recordsToLoad = 0;
        }
    }
    
    private void onAllRecordsLoaded()
    {
        Log.d(LOG_TAG, "SIMRecords: record load complete");

        String operator = getSIMOperatorNumeric();

        // Some fields require more than one SIM record to set

        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_NUMERIC, operator);

        if (imsi != null) {
            phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ISO_COUNTRY,
                                        MccTable.countryCodeForMcc(
                                            Integer.parseInt(imsi.substring(0,3))));
        }
        else {
            Log.e("SIM", "[SIMRecords] onAllRecordsLoaded: imsi is NULL!");
        }

        setVoiceMailByCountry(operator);
        setSpnFromConfig(operator);

        recordsLoadedRegistrants.notifyRegistrants(
            new AsyncResult(null, null, null));
        phone.mSimCard.broadcastSimStateChangedIntent(
                SimCard.INTENT_VALUE_SIM_LOADED, null);
    }

    private void setSpnFromConfig(String carrier) {
        if (mSpnOverride.containsCarrier(carrier)) {
            spn = mSpnOverride.getSpn(carrier);
        }
    }

    private void setVoiceMailByCountry (String spn) {
        if (mVmConfig.containsCarrier(spn)) {
            isVoiceMailFixed = true;
            voiceMailNum = mVmConfig.getVoiceMailNumber(spn);
            voiceMailTag = mVmConfig.getVoiceMailTag(spn);
        }
    }

    private void onSimReady() {
        /* broadcast intent SIM_READY here so that we can make sure
          READY is sent before IMSI ready
        */
        phone.mSimCard.broadcastSimStateChangedIntent(
                SimCard.INTENT_VALUE_SIM_READY, null);

        fetchSimRecords();
    }

    private void fetchSimRecords() {
        recordsRequested = true;

	Log.v(LOG_TAG, "SIMRecords:fetchSimRecords " + recordsToLoad);

        phone.mCM.getIMSI(obtainMessage(EVENT_GET_IMSI_DONE));
        recordsToLoad++;

        phone.mSIMFileHandler.loadEFTransparent(EF_ICCID, 
                            obtainMessage(EVENT_GET_ICCID_DONE));
        recordsToLoad++;

        // FIXME should examine EF[MSISDN]'s capability configuration
        // to determine which is the voice/data/fax line
        new AdnRecordLoader(phone).loadFromEF(EF_MSISDN, EF_EXT1, 1,
                    obtainMessage(EVENT_GET_MSISDN_DONE));
        recordsToLoad++;

        // Record number is subscriber profile
        phone.mSIMFileHandler.loadEFLinearFixed(EF_MBI, 1, 
                        obtainMessage(EVENT_GET_MBI_DONE));
        recordsToLoad++;

        phone.mSIMFileHandler.loadEFTransparent(EF_AD,  
                        obtainMessage(EVENT_GET_AD_DONE));
        recordsToLoad++;

        // Record number is subscriber profile
        phone.mSIMFileHandler.loadEFLinearFixed(EF_MWIS, 1, 
                        obtainMessage(EVENT_GET_MWIS_DONE));
        recordsToLoad++;


        // Also load CPHS-style voice mail indicator, which stores
        // the same info as EF[MWIS]. If both exist, both are updated
        // but the EF[MWIS] data is preferred
        // Please note this must be loaded after EF[MWIS]
        phone.mSIMFileHandler.loadEFTransparent(
                EF_VOICE_MAIL_INDICATOR_CPHS, 
                obtainMessage(EVENT_GET_VOICE_MAIL_INDICATOR_CPHS_DONE));
        recordsToLoad++;

        // Same goes for Call Forward Status indicator: fetch both
        // EF[CFIS] and CPHS-EF, with EF[CFIS] preferred.
        phone.mSIMFileHandler.loadEFLinearFixed(EF_CFIS, 1, obtainMessage(EVENT_GET_CFIS_DONE));
        recordsToLoad++;
        phone.mSIMFileHandler.loadEFTransparent(EF_CFF_CPHS,
                obtainMessage(EVENT_GET_CFF_DONE));
        recordsToLoad++;


        getSpnFsm(true, null);

        phone.mSIMFileHandler.loadEFTransparent(EF_SPDI, 
            obtainMessage(EVENT_GET_SPDI_DONE));
        recordsToLoad++;

        phone.mSIMFileHandler.loadEFLinearFixed(EF_PNN, 1,
              obtainMessage(EVENT_GET_PNN_DONE));
        recordsToLoad++;



        phone.mSIMFileHandler.loadEFTransparent(EF_INFO_CPHS,
                obtainMessage(EVENT_GET_INFO_CPHS_DONE));
        recordsToLoad++;

        //Read OPL file and cache it
        updateOplCache();

        //Read PNN file and cache it
        updatePnnCache();

        phone.mSIMFileHandler.loadEFTransparent(EF_SST,
            obtainMessage(EVENT_GET_SST_DONE));
        recordsToLoad++;

        //persist.cust.tel.adapt is super flag, if this is set then EF_CSP
        //will be used irrespective of the value of
        //persist.cust.tel.efcsp.plmn.Otherwise EF_CSP will be used if
        //persist.cust.tel.efcsp.plmn is set.
        if (SystemProperties.getBoolean("persist.cust.tel.adapt",false) ||
            SystemProperties.getBoolean("persist.cust.tel.efcsp.plmn",false)) {
            phone.mSIMFileHandler.loadEFTransparent(EF_CSP_CPHS,
                 obtainMessage(EVENT_GET_CSP_CPHS_DONE));
            recordsToLoad++;
        }
        // XXX should seek instead of examining them all
        if (false) { // XXX
            phone.mSIMFileHandler.loadEFLinearFixedAll(EF_SMS,
                obtainMessage(EVENT_GET_ALL_SMS_DONE));
            recordsToLoad++;
        }

        if (CRASH_RIL) {
            String sms = "0107912160130310f20404d0110041007030208054832b0120ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
            byte[] ba = SimUtils.hexStringToBytes(sms);

            phone.mSIMFileHandler.updateEFLinearFixed(EF_SMS, 1, ba, null,
                            obtainMessage(EVENT_MARK_SMS_READ_DONE, 1));
        }
    }

    /**
     * Returns the SpnDisplayRule based on settings on the SIM and the
     * specified plmn (currently-registered PLMN).  See TS 22.101 Annex A
     * and TS 51.011 10.3.11 for details.
     *
     * If the SPN is not found on the SIM, the rule is always PLMN_ONLY.
     */
    int getDisplayRule(String plmn) {
        int rule;
        if (spn == null || spnDisplayCondition == -1) {
            // EF_SPN was not found on the SIM, or not yet loaded.  Just show ONS.
            rule = SPN_RULE_SHOW_PLMN;
        } else if (isOnMatchingPlmn(plmn)) {
            rule = SPN_RULE_SHOW_SPN;
            if ((spnDisplayCondition & 0x01) == 0x01) {
                // ONS required when registered to HPLMN or PLMN in EF_SPDI
                rule |= SPN_RULE_SHOW_PLMN;
            }
        } else {
            rule = SPN_RULE_SHOW_PLMN;
            if ((spnDisplayCondition & 0x02) == 0x00) {
                // SPN required if not registered to HPLMN or PLMN in EF_SPDI
                rule |= SPN_RULE_SHOW_SPN;
            }
        }
        return rule;
    }

    /**
     * Checks if plmn is HPLMN or on the spdiNetworks list.
     */
    private boolean isOnMatchingPlmn(String plmn) {
        if (plmn == null) return false;

        if (plmn.equals(getSIMOperatorNumeric())) {
            return true;
        }

        if (spdiNetworks != null) {
            for (String spdiNet : spdiNetworks) {
                if (plmn.equals(spdiNet)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * States of Get SPN Finite State Machine which only used by getSpnFsm()
     */
    private enum Get_Spn_Fsm_State {
        IDLE,               // No initialized
        INIT,               // Start FSM
        READ_SPN_3GPP,      // Load EF_SPN firstly
        READ_SPN_CPHS,      // Load EF_SPN_CPHS secondly
        READ_SPN_SHORT_CPHS // Load EF_SPN_SHORT_CPHS last
    }

    /**
     * Finite State Machine to load Service Provider Name , which can be stored
     * in either EF_SPN (3GPP), EF_SPN_CPHS, or EF_SPN_SHORT_CPHS (CPHS4.2)
     *
     * After starting, FSM will search SPN EFs in order and stop after finding
     * the first valid SPN
     *
     * @param start set true only for initialize loading
     * @param ar the AsyncResult from loadEFTransparent
     *        ar.exception holds exception in error
     *        ar.result is byte[] for data in success
     */
    private void getSpnFsm(boolean start, AsyncResult ar) {
        byte[] data;

        if (start) {
            spnState = Get_Spn_Fsm_State.INIT;
        }

        switch(spnState){
            case INIT:
                spn = null;

                phone.mSIMFileHandler.loadEFTransparent( EF_SPN,
                        obtainMessage(EVENT_GET_SPN_DONE));
                recordsToLoad++;

                spnState = Get_Spn_Fsm_State.READ_SPN_3GPP;
                break;
            case READ_SPN_3GPP:
                if (ar != null && ar.exception == null) {
                    data = (byte[]) ar.result;
                    spnDisplayCondition = 0xff & data[0];
                    spn = SimUtils.adnStringFieldToString(data, 1, data.length - 1);

                    if (DBG) log("Load EF_SPN: " + spn
                            + " spnDisplayCondition: " + spnDisplayCondition);
                    phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, spn);

                    spnState = Get_Spn_Fsm_State.IDLE;
                } else {
                    phone.mSIMFileHandler.loadEFTransparent( EF_SPN_CPHS,
                            obtainMessage(EVENT_GET_SPN_DONE));
                    recordsToLoad++;

                    spnState = Get_Spn_Fsm_State.READ_SPN_CPHS;
                    
                    // See TS 51.011 10.3.11.  Basically, default to
                    // show PLMN always, and SPN also if roaming.
                    spnDisplayCondition = -1;
                }
                break;
            case READ_SPN_CPHS:
                if (ar != null && ar.exception == null) {
                    data = (byte[]) ar.result;
                    spn = SimUtils.adnStringFieldToString(
                            data, 0, data.length - 1 );

                    if (DBG) log("Load EF_SPN_CPHS: " + spn);
                    phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, spn);

                    spnState = Get_Spn_Fsm_State.IDLE;
                } else {
                    phone.mSIMFileHandler.loadEFTransparent( EF_SPN_SHORT_CPHS,
                            obtainMessage(EVENT_GET_SPN_DONE));
                    recordsToLoad++;

                    spnState = Get_Spn_Fsm_State.READ_SPN_SHORT_CPHS;
                }
                break;
            case READ_SPN_SHORT_CPHS:
                if (ar != null && ar.exception == null) {
                    data = (byte[]) ar.result;
                    spn = SimUtils.adnStringFieldToString(
                            data, 0, data.length - 1);

                    if (DBG) log("Load EF_SPN_SHORT_CPHS: " + spn);
                    phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, spn);
                }else {
                    if (DBG) log("No SPN loaded in either CHPS or 3GPP");
                }

                spnState = Get_Spn_Fsm_State.IDLE;
                break;
            default:
                spnState = Get_Spn_Fsm_State.IDLE;
        }
    }

    /**
     * Function to match EF_OPL plmn with the registered plmn.
     * @param simPlmn, plmn read from EF_OPL
     * @param bcchPlmn, registered plmn
     * @param length, length of registered plmn
     * @return true if plmns match, otherwise false.
     */
    boolean matchSimPlmn (int simPlmn[],int bcchPlmn[],int length) {
       int wildCardDigit = 0x0D;
       boolean match = false;

       /*Apply '0' suffix rule*/
       if (simPlmn[5] == 0x0f) {
           simPlmn[5] = 0;
       }

       /*Check for wildcard digits in simPlmn and overwite them with the
        *corresponding digits in bcchPlmn.*/
       for (int i = 0;i < length;i++) {
            if (simPlmn[i] == wildCardDigit) {
                simPlmn[i] = bcchPlmn[i];
            }
       }

       /*Match MCC*/
       if ((simPlmn[0] == bcchPlmn[0]) &&
           (simPlmn[1] == bcchPlmn[1]) &&
           (simPlmn[2] == bcchPlmn[2])) {
            if(length == 5) {
               /*If the length of registered plmn is 5, then this is 2 digit MNC
                *case. Compare only first two digits of mnc*/
               match = ((simPlmn[3] == bcchPlmn[3]) &&
                       (simPlmn[4] == bcchPlmn[4]));
            }
            else {
               /*Otherwise Compare all digits of MNC*/
               match = ((simPlmn[3] == bcchPlmn[3]) &&
                       (simPlmn[4] == bcchPlmn[4]) &&
                       (simPlmn[5] == bcchPlmn[5]));
            }
       }
       return match;
    }


    /**
     * Parse TS 51.011 EF[SPDI] record
     * This record contains the list of numeric network IDs that
     * are treated specially when determining SPN display
     */
    private void
    parseEfSpdi(byte[] data)
    {
        SimTlv tlv = new SimTlv(data, 0, data.length);

        byte[] plmnEntries = null;

        // There should only be one TAG_SPDI_PLMN_LIST
        for ( ; tlv.isValidObject() ; tlv.nextObject()) {
            if (tlv.getTag() == TAG_SPDI_PLMN_LIST) {
                plmnEntries = tlv.getData();
                break;
            }
        }

        if (plmnEntries == null) {
            return;
        }

        spdiNetworks = new ArrayList<String>(plmnEntries.length / 3);

        for (int i = 0 ; i + 2 < plmnEntries.length ; i += 3) {
            String plmnCode;        
            plmnCode = SimUtils.bcdToString(plmnEntries, i, 3);

            // Valid operator codes are 5 or 6 digits
            if (plmnCode.length() >= 5) {
                log("EF_SPDI network: " + plmnCode);
                spdiNetworks.add(plmnCode);
            }
        }
    }

    /**
     * check to see if Mailbox Number is allocated and activated in CPHS SST
     */
    private boolean isCphsMailboxEnabled() {
        if (mCphsInfo == null)  return false;
        return ((mCphsInfo[1] & CPHS_SST_MBN_MASK) == CPHS_SST_MBN_ENABLED );
    }

    private void log(String s) {
        Log.d(LOG_TAG, "[SIMRecords] " + s);
    }

    /**
     *process EF CSP data and check whether network operator menu item in
     *Call Settings menu,is to be enabled/disabled
     */
    private void processEFCspData() {
       int i = 0;
       /*
        *According to doc CPHS4_2.WW6,CPHS B.4.7.1,elementary file
        *EF_CSP contians CPHS defined 18 bytes (i.e 9 service groups info) and
        *additional records specific to operator if any
       */
       int used_csp_groups = 13;
       /*
       * This is the Servive group number of the service we need to check.
       * This represents value added services group.
       */
       byte value_added_services_group = (byte)0xC0;
       Message msg = obtainMessage(EVENT_AUTO_SELECT_DONE);
       for (i = 0;i < used_csp_groups;i++) {
           if (cspCphsInfo[2*i] == value_added_services_group) {
               Log.i(CSP_TAG, "sevice group 0xC0,value " + cspCphsInfo[(2*i) +1]);
               //If bit 8 is set then the value is negative since it is signed
               //byte,so checking value to be less than zero.
               if (cspCphsInfo[(2*i)+1] < 0) {
                  //Bit 8 is for Restriction of menu options
                  //for manual PLMN selection
                  cspPlmn = 1;
               } else {
                  cspPlmn = 0;
                 // Manual Network Selection option is disabled, so enable
                 // Automatic Network Selection mode.
                 phone.setNetworkSelectionModeAutomatic(msg);
               }
               return;
           }
       }
       Log.e(CSP_TAG, "sevice group 0xC0,Not founf in EF CSP");
    }
    /**
     * Update OPL cache from EF_OPL file.
     */
    void updateOplCache() {
        Log.i(EONS_TAG,"Updating OPL Cache");
        phone.mSIMFileHandler.loadEFLinearFixedAll(EF_OPL,
              obtainMessage(EVENT_GET_ALL_OPL_RECORDS_DONE));
        recordsToLoad++;
    }

    /**
     * Update PNN cache from EF_PNN file.
     */
    void updatePnnCache() {
        Log.i(EONS_TAG,"Updating PNN Cache");
        phone.mSIMFileHandler.loadEFLinearFixedAll(EF_PNN,
              obtainMessage(EVENT_GET_ALL_PNN_RECORDS_DONE));
        recordsToLoad++;
    }

    /**
     * Parse OPL file data for matching record.
     */
    private void  displayEonsName(int flag) {
       int count = 0;
       int hLac;
       int ind = 0;
       int simPlmn[] = {0,0,0,0,0,0};
       int bcchPlmn[] = {0,0,0,0,0,0};
       int bcchPlmnLength = 0;
       String regOperator;

       if (oplCache == null) {
          //If the cache is null, probably there is an exception in reading
          //records from EF_OPl file, display name form ME database.
          Log.w(EONS_TAG,"oplCache is null.");
          return;
       }
       count = oplCache.size();

       if (flag == 1)
           regOperator = phone.mSST.newSS.getOperatorNumeric();
       else
           regOperator = phone.mSST.ss.getOperatorNumeric();
       /* We need the registered network plmn data to parse EF_OPL and
        * EF_PNN data. So do not process EF_OPL and EF_PNN data if the
        * registration is not done yet.*/
       if((regOperator == null) || (regOperator.trim().length() == 0)) {
          Log.w(EONS_TAG,"Registered operator is null or empty.");
          useMEName();
          return;
       }

       Log.i(EONS_TAG,"Number of OPL records = " + count);
       oplDataPresent = true;
       hLac = -1;
       GsmCellLocation loc = ((GsmCellLocation)phone.getCellLocation());
       if (loc != null) hLac = loc.getLac();
       if (hLac == -1) {
          Log.w(EONS_TAG,"Registered Lac is -1.");
          return;
       }

       try {
          for (ind = 0; ind < count; ind++) {
             byte[] data = (byte[]) oplCache.get(ind);

             /*Split the OPL PLMN data into digits*/
             simPlmn[0] = data[0]&0x0f;     /*mcc1*/
             simPlmn[1] = (data[0]>>4)&0x0f;/*mcc2*/
             simPlmn[2] = data[1]&0x0f;     /*mcc3*/
             simPlmn[3] = data[2]&0x0f;     /*mnc1*/
             simPlmn[4] = (data[2]>>4)&0x0f;/*mnc2*/
             simPlmn[5] = (data[1]>>4)&0x0f;/*mnc3*/

             /*Convert bcch plmn from ASCII to bcd*/
             bcchPlmnLength = regOperator.length();
             for (int ind1 = 0;ind1 < bcchPlmnLength;ind1++) {
                  bcchPlmn[ind1] = regOperator.charAt(ind1) - '0';
             }

             /*MSB of LAC comes first and then LSB according to TS 24.008[47]*/
             oplDataLac1 = ((data[3]&0xff)<<8) | (data[4]&0xff);
             oplDataLac2 = ((data[5]&0xff)<<8) | (data[6]&0xff);
             oplDataPnnNum  = (short)(data[7]&0xff);
             Log.d(EONS_TAG,"lac1=" + oplDataLac1 + " lac2=" + oplDataLac2 +
             " hLac=" + hLac + " pnn rec=" + oplDataPnnNum);
             /*Check EF_OPL's mccmnc is same as registered  PLMN*/
             if(matchSimPlmn(simPlmn,bcchPlmn,bcchPlmnLength)) {
                /*Check if HLAC is with in range of EF_OPL LACs*/
                if ((oplDataLac1 <= hLac) && (hLac <= oplDataLac2)) {
                   if ((oplDataPnnNum > 0x00) && (oplDataPnnNum < 0xFF)) {
                      /*
                       *We have a valid PNN record number in EF_OPL,
                       *Read the PNN record from EF_PNN.
                       */
                      Log.w(EONS_TAG," lac1=" + oplDataLac1 + " lac2=" + oplDataLac2 +
                            " hLac=" + hLac + " pnn rec=" + oplDataPnnNum);
                      getNameFromPnnRecord(oplDataPnnNum);
                      break;
                   }
                   else {
                      oplDataPresent = false;
                      Log.w("LOG_TAG",
                            "PNN record number in EF_OPL is not valid");
                   }
                }
                else {
                    oplDataPresent = false;
                    Log.w(EONS_TAG,
                          "HLAC is not with in range of EF_OPL's LACs,ignoring pnn data, "+
                          "hLac=" + hLac + " lac1=" + oplDataLac1 + " lac2=" + oplDataLac2);
                }
             }
             else {
                 oplDataPresent = false;
                 Log.w(EONS_TAG,
                      "plmn in EF_OPL doesnot match reg plmn,ignoring pnn data sim plmn "+
                      simPlmn[0]+simPlmn[1]+simPlmn[2]+simPlmn[3]+simPlmn[4]+simPlmn[5]+",bcch plmn "
                      +bcchPlmn[0]+bcchPlmn[1]+bcchPlmn[2]+bcchPlmn[3]+bcchPlmn[4]+bcchPlmn[5]);
             }
          }
       } catch (Exception e) {
          Log.e(EONS_TAG,"Exception while processing OPL data " + e);
       }

       if (ind >= count) {
          //If there is no matching record in the EF_OPL file, then display
          //name from ME database.
          Log.w(EONS_TAG,"No matching OPL record found, using default method");
          useMEName();
       }
    }

    void fetchPnnFirstRecord(int flag) {
        String regOperator;
        if (flag == 1)
            regOperator = phone.mSST.newSS.getOperatorNumeric();
        else
            regOperator = phone.mSST.ss.getOperatorNumeric();
        Log.i(EONS_TAG,"Comparing hplmn " + getSIMOperatorNumeric() +
                       " with reg plmn " + regOperator);
        //If the registered PLMN is HPLMN, then fetch first record of EF_PNN
        oplDataPresent = false;
        if ((getSIMOperatorNumeric() != null) &&
             getSIMOperatorNumeric().equals(regOperator)) {
             Log.i(EONS_TAG,"Fetching EF_PNN's first record");
             getNameFromPnnRecord(1);
        }
        else {
             pnnDataPresent = false;
             phone.mSST.updateSpnDisplayWrapper();
        }
    }

    void handleSstData(byte[] data) {
       try {
            if (SystemProperties.getBoolean("persist.cust.tel.simtype",false)) {
                //2G Sim.
                //Service no 51:   PLMN Network Name
                //Service no 52:   Operator PLMN List
                //Check if these services are allocated and activated.
                sstPlmnOplValue = ((data[12]>>4) & 0x0F);
                if (sstPlmnOplValue == 0x0F) {
                    sstPlmnOplValue = PNN_OPL_ENABLED;
                    Log.i(EONS_TAG,"SST: 2G Sim,PNN and OPL services enabled "+sstPlmnOplValue);
                }
                else if (sstPlmnOplValue == 0x03) {
                    sstPlmnOplValue = ONLY_PNN_ENABLED;
                    Log.i(EONS_TAG,"SST: 2G Sim,PNN enabled, OPL disabled "+sstPlmnOplValue);
                }
                else {
                    sstPlmnOplValue = EONS_DISABLED;
                    Log.i(EONS_TAG,"SST: 2G Sim,PNN disabled, disabling EONS "+sstPlmnOplValue);
                }
            }
            else {
               //3G Sim.
               //Service no 45: PLMN Network Name
               //Service no 46: Operator PLMN List
               //Check if these services are available.
               sstPlmnOplValue = ((data[5]>>4) & 0x03);
               if (sstPlmnOplValue == 0x03) {
                   sstPlmnOplValue = PNN_OPL_ENABLED;
                   Log.i(EONS_TAG,"SST: 3G Sim,PNN and OPL services enabled "+sstPlmnOplValue);
               }
               else if (sstPlmnOplValue == 0x01) {
                   sstPlmnOplValue = ONLY_PNN_ENABLED;
                   Log.i(EONS_TAG,"SST: 3G Sim,PNN enabled, OPL disabled "+sstPlmnOplValue);
               }
               else {
                   sstPlmnOplValue = EONS_DISABLED;
                   Log.i(EONS_TAG,"SST: 3G Sim,PNN disabled, disabling EONS "+sstPlmnOplValue);
               }
            }
            /*Update the display*/
            if (sstPlmnOplValue == EONS_DISABLED) {
                phone.mSST.updateSpnDisplayWrapper();
            }
       } catch(Exception e){
           Log.e(EONS_TAG,"Exception in processing SST Data " + e);
       }
    }

    void useMEName() {
       oplDataPresent = false;
       pnnDataPresent = false;
       phone.mSST.updateSpnDisplayWrapper();
    }

    /*
     * Update the EONS name from PNN cache for the given record number.
     * @param record, PNN record number.
     */
    void getNameFromPnnRecord(int record) {
       int length = 0;
       if ((pnnCache == null) || (record > pnnCache.size() || record < 1)) {
          //If the cache is null, probably there is an exception in reading
          //records from EF_PNN file, display name form ME database.
          Log.w(EONS_TAG,"pnnCache is null/Invalid PNN Rec, using default method");
          useMEName();
          return;
       }
       Log.i(EONS_TAG,"Number of PNN records = " + pnnCache.size());

       try {
          byte[] data = (byte[]) pnnCache.get(record - 1);
          Log.d(EONS_TAG,"PNN record number " + record + ", hex data " +
                SimUtils.bytesToHexString(data) );
          pnnDataPresent = true;
          /*Some times the EF_PNN file may be present but the data contained it
           *may be invalid, i.e 0xFF,checking few mandatory feilds like long IEI
           *and Legth of long name not to be invalid i.e 0xFF*/
          if((data[0] != -1) && (data[1] != -1)) {
             /*Length of Long Name*/
             length = data[1];
             Log.d(EONS_TAG,"PNN longname length : " + length );
             pnnDataLongName = SimUtils.networkNameToString(data, 2, length);
             Log.i(EONS_TAG,"PNN longname : " + pnnDataLongName );
             if((data[length + 2] != -1) && (data[length + 3] != -1)) {
                Log.d(EONS_TAG,"PNN shortname length : " + data[length+3] );
                pnnDataShortName = SimUtils.networkNameToString(data,
                      length+4,data[length + 3]);
                Log.d(EONS_TAG,"PNN shortname : " + pnnDataShortName );
             }
             else {
                /*Short Name is not mandatory and its absence is not an error*/
                Log.e(EONS_TAG, "EF_PNN: No short Name");
             }
          }
          else {
             /*Invaid EF_PNN data*/
             pnnDataPresent = false;
             Log.e(EONS_TAG, "EF_PNN: Invalid EF_PNN Data");
          }
       }catch(Exception e) {
          Log.e(EONS_TAG, "Exception while processing PNN data " + e);
       }
       phone.mSST.updateSpnDisplayWrapper();
    }

    public ArrayList<NetworkInfo> getEonsAvailableNetworks(ArrayList<NetworkInfo> avlNetworks) {
       ArrayList<NetworkInfo> eonsList = null;
       if ((getOnsAlg() == EONS_ALG) && (avlNetworks != null)) {
          int size = avlNetworks.size();
          String pnnName = null;
          NetworkInfo ni;
          eonsList = new ArrayList<NetworkInfo>(size);
          Log.i(EONS_TAG,"Available Networks List Size = " + size);
          for(int i = 0;i < size;i++) {
             ni = (NetworkInfo) avlNetworks.get(i);
             pnnName = getEonsNameFromPnn(ni.getOperatorNumeric());
             Log.i(EONS_TAG,"PLMN=" + ni.getOperatorNumeric() + " ME Name="
                   + ni.getOperatorAlphaLong() + " PNN Name=" + pnnName);
             if (pnnName == null) {
                pnnName = ni.getOperatorAlphaLong();
             }
             eonsList.add (new NetworkInfo(pnnName,ni.getOperatorAlphaShort(),
                      ni.getOperatorNumeric(),ni.getState()));
          }
       }
       return eonsList;
    }

   private String getEonsNameFromPnn(String plmn) {
       String name = null;
       int count = 0;
       int ind = 0;
       int simPlmn[] = {0,0,0,0,0,0};
       int bcchPlmn[] = {0,0,0,0,0,0};
       int bcchPlmnLength = 0;
       short recNum = 0;
       if (oplCache == null) {
          Log.w(EONS_TAG,"getEonsNameFromPnn() oplCache is null.");
          return name;
       }
       count = oplCache.size();
       Log.d(EONS_TAG,"getEonsNameFromPnn() Number of OPL records = " + count);
       try {
          for (ind = 0; ind < count; ind++) {
             byte[] data = (byte[]) oplCache.get(ind);

             simPlmn[0] = data[0]&0x0f;     /*mcc1*/
             simPlmn[1] = (data[0]>>4)&0x0f;/*mcc2*/
             simPlmn[2] = data[1]&0x0f;     /*mcc3*/
             simPlmn[3] = data[2]&0x0f;     /*mnc1*/
             simPlmn[4] = (data[2]>>4)&0x0f;/*mnc2*/
             simPlmn[5] = (data[1]>>4)&0x0f;/*mnc3*/

             bcchPlmnLength = plmn.length();
             for (int ind1 = 0;ind1 < bcchPlmnLength;ind1++) {
                bcchPlmn[ind1] = plmn.charAt(ind1) - '0';
             }

             recNum  = (short)(data[7]&0xff);
             if(matchSimPlmn(simPlmn,bcchPlmn,bcchPlmnLength)) {
                if ((pnnCache == null) || (recNum > pnnCache.size() || recNum < 1)) {
                   Log.w(EONS_TAG,"getEonsNameFromPnn(), pnnCache is null/Invalid PNN Rec");
                }
                else {
                   byte[] data1 = (byte[]) pnnCache.get(recNum - 1);
                   if((data1[0] != -1) && (data1[1] != -1)) {
                      name = SimUtils.networkNameToString(data1, 2, data1[1]);
                      Log.d(EONS_TAG,"getEonsNameFromPnn() Long Name: " + name );
                   }
                }
                break;
             }
          }

          if (ind >= count) {
             Log.w(EONS_TAG,"getEonsNameFromPnn(),No matching OPL record found");
             name = null;
          }
       } catch (Exception e) {
          Log.e(EONS_TAG,"getEonsNameFromPnn(),Exception while processing OPL data " + e);
          name = null;
       }
       return name;
   }

   public int getSstPlmnOplValue() {
      return sstPlmnOplValue;
   }
}
