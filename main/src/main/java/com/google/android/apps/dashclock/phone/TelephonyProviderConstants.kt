/*
 * Copyright 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.phone

import android.net.Uri
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.Patterns
import java.util.regex.Pattern

/**
 * Private API. Will likely break in the future.
 *
 * Excerpt from frameworks/base/core/java/android/provider/Telephony.java.
 */
class TelephonyProviderConstants private constructor() {

    /**
     * Base columns for tables that contain text based SMSs.
     */
    interface TextBasedSmsColumns {
        companion object {
            const val TYPE = "type"

            const val MESSAGE_TYPE_ALL = 0
            const val MESSAGE_TYPE_INBOX = 1
            const val MESSAGE_TYPE_SENT = 2
            const val MESSAGE_TYPE_DRAFT = 3
            const val MESSAGE_TYPE_OUTBOX = 4
            const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
            const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later

            const val THREAD_ID = "thread_id"
            const val ADDRESS = "address"
            const val PERSON_ID = "person"
            const val DATE = "date"
            const val DATE_SENT = "date_sent"
            const val READ = "read"
            const val SEEN = "seen"
            const val STATUS = "status"

            const val STATUS_NONE = -1
            const val STATUS_COMPLETE = 0
            const val STATUS_PENDING = 32
            const val STATUS_FAILED = 64

            const val SUBJECT = "subject"
            const val BODY = "body"
            const val PERSON = "person"
            const val PROTOCOL = "protocol"
            const val REPLY_PATH_PRESENT = "reply_path_present"
            const val SERVICE_CENTER = "service_center"
            const val LOCKED = "locked"
            const val ERROR_CODE = "error_code"
            const val META_DATA = "meta_data"
        }
    }

    /**
     * Contains all text based SMS messages.
     */
    class Sms : BaseColumns, TextBasedSmsColumns {
        class Inbox : BaseColumns, TextBasedSmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://sms/inbox")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Sent : BaseColumns, TextBasedSmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://sms/sent")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Draft : BaseColumns, TextBasedSmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://sms/draft")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Outbox : BaseColumns, TextBasedSmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://sms/outbox")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Conversations : BaseColumns, TextBasedSmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://sms/conversations")
                const val DEFAULT_SORT_ORDER = "date DESC"
                const val SNIPPET = "snippet"
                const val MESSAGE_COUNT = "msg_count"
            }
        }

        class Intents {
            companion object {
                const val RESULT_SMS_HANDLED = 1
                const val RESULT_SMS_GENERIC_ERROR = 2
                const val RESULT_SMS_OUT_OF_MEMORY = 3
                const val RESULT_SMS_UNSUPPORTED = 4

                const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
                const val DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED"
                const val WAP_PUSH_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_RECEIVED"
                const val SMS_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_CB_RECEIVED"
                const val SMS_EMERGENCY_CB_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_EMERGENCY_CB_RECEIVED"
                const val SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED"
                const val SIM_FULL_ACTION = "android.provider.Telephony.SIM_FULL"
                const val SMS_REJECTED_ACTION = "android.provider.Telephony.SMS_REJECTED"
            }
        }

        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT

            const val TYPE = TextBasedSmsColumns.TYPE
            const val MESSAGE_TYPE_ALL = TextBasedSmsColumns.MESSAGE_TYPE_ALL
            const val MESSAGE_TYPE_INBOX = TextBasedSmsColumns.MESSAGE_TYPE_INBOX
            const val MESSAGE_TYPE_SENT = TextBasedSmsColumns.MESSAGE_TYPE_SENT
            const val MESSAGE_TYPE_DRAFT = TextBasedSmsColumns.MESSAGE_TYPE_DRAFT
            const val MESSAGE_TYPE_OUTBOX = TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX
            const val MESSAGE_TYPE_FAILED = TextBasedSmsColumns.MESSAGE_TYPE_FAILED
            const val MESSAGE_TYPE_QUEUED = TextBasedSmsColumns.MESSAGE_TYPE_QUEUED
            const val THREAD_ID = TextBasedSmsColumns.THREAD_ID
            const val ADDRESS = TextBasedSmsColumns.ADDRESS
            const val PERSON_ID = TextBasedSmsColumns.PERSON_ID
            const val DATE = TextBasedSmsColumns.DATE
            const val DATE_SENT = TextBasedSmsColumns.DATE_SENT
            const val READ = TextBasedSmsColumns.READ
            const val SEEN = TextBasedSmsColumns.SEEN
            const val STATUS = TextBasedSmsColumns.STATUS
            const val STATUS_NONE = TextBasedSmsColumns.STATUS_NONE
            const val STATUS_COMPLETE = TextBasedSmsColumns.STATUS_COMPLETE
            const val STATUS_PENDING = TextBasedSmsColumns.STATUS_PENDING
            const val STATUS_FAILED = TextBasedSmsColumns.STATUS_FAILED
            const val SUBJECT = TextBasedSmsColumns.SUBJECT
            const val BODY = TextBasedSmsColumns.BODY
            const val PERSON = TextBasedSmsColumns.PERSON
            const val PROTOCOL = TextBasedSmsColumns.PROTOCOL
            const val REPLY_PATH_PRESENT = TextBasedSmsColumns.REPLY_PATH_PRESENT
            const val SERVICE_CENTER = TextBasedSmsColumns.SERVICE_CENTER
            const val LOCKED = TextBasedSmsColumns.LOCKED
            const val ERROR_CODE = TextBasedSmsColumns.ERROR_CODE
            const val META_DATA = TextBasedSmsColumns.META_DATA

            @JvmField
            val CONTENT_URI: Uri = Uri.parse("content://sms")

            const val DEFAULT_SORT_ORDER = "date DESC"

            @JvmStatic
            fun isOutgoingFolder(messageType: Int): Boolean {
                return messageType == MESSAGE_TYPE_FAILED
                        || messageType == MESSAGE_TYPE_OUTBOX
                        || messageType == MESSAGE_TYPE_SENT
                        || messageType == MESSAGE_TYPE_QUEUED
            }
        }
    }

    /**
     * Base columns for tables that contain MMSs.
     */
    interface BaseMmsColumns : BaseColumns {
        companion object {
            const val MESSAGE_BOX_ALL = 0
            const val MESSAGE_BOX_INBOX = 1
            const val MESSAGE_BOX_SENT = 2
            const val MESSAGE_BOX_DRAFTS = 3
            const val MESSAGE_BOX_OUTBOX = 4

            const val DATE = "date"
            const val DATE_SENT = "date_sent"
            const val MESSAGE_BOX = "msg_box"
            const val READ = "read"
            const val SEEN = "seen"
            const val MESSAGE_ID = "m_id"
            const val SUBJECT = "sub"
            const val SUBJECT_CHARSET = "sub_cs"
            const val CONTENT_TYPE = "ct_t"
            const val CONTENT_LOCATION = "ct_l"
            const val FROM = "from"
            const val TO = "to"
            const val CC = "cc"
            const val BCC = "bcc"
            const val EXPIRY = "exp"
            const val MESSAGE_CLASS = "m_cls"
            const val MESSAGE_TYPE = "m_type"
            const val MMS_VERSION = "v"
            const val MESSAGE_SIZE = "m_size"
            const val PRIORITY = "pri"
            const val READ_REPORT = "rr"
            const val REPORT_ALLOWED = "rpt_a"
            const val RESPONSE_STATUS = "resp_st"
            const val STATUS = "st"
            const val TRANSACTION_ID = "tr_id"
            const val RETRIEVE_STATUS = "retr_st"
            const val RETRIEVE_TEXT = "retr_txt"
            const val RETRIEVE_TEXT_CHARSET = "retr_txt_cs"
            const val READ_STATUS = "read_status"
            const val CONTENT_CLASS = "ct_cls"
            const val DELIVERY_REPORT = "d_rpt"
            const val DELIVERY_TIME_TOKEN = "d_tm_tok"
            const val DELIVERY_TIME = "d_tm"
            const val RESPONSE_TEXT = "resp_txt"
            const val SENDER_VISIBILITY = "s_vis"
            const val REPLY_CHARGING = "r_chg"
            const val REPLY_CHARGING_DEADLINE_TOKEN = "r_chg_dl_tok"
            const val REPLY_CHARGING_DEADLINE = "r_chg_dl"
            const val REPLY_CHARGING_ID = "r_chg_id"
            const val REPLY_CHARGING_SIZE = "r_chg_sz"
            const val PREVIOUSLY_SENT_BY = "p_s_by"
            const val PREVIOUSLY_SENT_DATE = "p_s_d"
            const val STORE = "store"
            const val MM_STATE = "mm_st"
            const val MM_FLAGS_TOKEN = "mm_flg_tok"
            const val MM_FLAGS = "mm_flg"
            const val STORE_STATUS = "store_st"
            const val STORE_STATUS_TEXT = "store_st_txt"
            const val STORED = "stored"
            const val TOTALS = "totals"
            const val MBOX_TOTALS = "mb_t"
            const val MBOX_TOTALS_TOKEN = "mb_t_tok"
            const val QUOTAS = "qt"
            const val MBOX_QUOTAS = "mb_qt"
            const val MBOX_QUOTAS_TOKEN = "mb_qt_tok"
            const val MESSAGE_COUNT = "m_cnt"
            const val START = "start"
            const val DISTRIBUTION_INDICATOR = "d_ind"
            const val ELEMENT_DESCRIPTOR = "e_des"
            const val LIMIT = "limit"
            const val RECOMMENDED_RETRIEVAL_MODE = "r_r_mod"
            const val RECOMMENDED_RETRIEVAL_MODE_TEXT = "r_r_mod_txt"
            const val STATUS_TEXT = "st_txt"
            const val APPLIC_ID = "apl_id"
            const val REPLY_APPLIC_ID = "r_apl_id"
            const val AUX_APPLIC_ID = "aux_apl_id"
            const val DRM_CONTENT = "drm_c"
            const val ADAPTATION_ALLOWED = "adp_a"
            const val REPLACE_ID = "repl_id"
            const val CANCEL_ID = "cl_id"
            const val CANCEL_STATUS = "cl_st"
            const val THREAD_ID = "thread_id"
            const val LOCKED = "locked"
            const val META_DATA = "meta_data"
        }
    }

    /**
     * Helper functions for the "threads" table used by MMS and SMS.
     * Added by Roman.
     */
    class CanonicalAddresses private constructor() : CanonicalAddressesColumns {
        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT
            const val ADDRESS = CanonicalAddressesColumns.ADDRESS

            @JvmField
            val CONTENT_URI: Uri = Uri.withAppendedPath(
                MmsSms.CONTENT_URI, "canonical-addresses"
            )

            @JvmField
            val OBSOLETE_THREADS_URI: Uri = Uri.withAppendedPath(
                CONTENT_URI, "obsolete"
            )
        }
    }

    /**
     * Columns for the "canonical_addresses" table used by MMS and
     * SMS.
     */
    interface CanonicalAddressesColumns : BaseColumns {
        companion object {
            const val ADDRESS = "address"
        }
    }

    /**
     * Columns for the "threads" table used by MMS and SMS.
     */
    interface ThreadsColumns : BaseColumns {
        companion object {
            const val DATE = "date"
            const val RECIPIENT_IDS = "recipient_ids"
            const val MESSAGE_COUNT = "message_count"
            const val READ = "read"
            const val SNIPPET = "snippet"
            const val SNIPPET_CHARSET = "snippet_cs"
            const val TYPE = "type"
            const val ERROR = "error"
            const val HAS_ATTACHMENT = "has_attachment"
        }
    }

    /**
     * Helper functions for the "threads" table used by MMS and SMS.
     */
    class Threads private constructor() : ThreadsColumns {
        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT
            const val DATE = ThreadsColumns.DATE
            const val RECIPIENT_IDS = ThreadsColumns.RECIPIENT_IDS
            const val MESSAGE_COUNT = ThreadsColumns.MESSAGE_COUNT
            const val READ = ThreadsColumns.READ
            const val SNIPPET = ThreadsColumns.SNIPPET
            const val SNIPPET_CHARSET = ThreadsColumns.SNIPPET_CHARSET
            const val TYPE = ThreadsColumns.TYPE
            const val ERROR = ThreadsColumns.ERROR
            const val HAS_ATTACHMENT = ThreadsColumns.HAS_ATTACHMENT

            private val ID_PROJECTION = arrayOf(BaseColumns._ID)
            private const val STANDARD_ENCODING = "UTF-8"
            private val THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/threadID")

            @JvmField
            val CONTENT_URI: Uri = Uri.withAppendedPath(
                MmsSms.CONTENT_URI, "conversations"
            )

            @JvmField
            val OBSOLETE_THREADS_URI: Uri = Uri.withAppendedPath(
                CONTENT_URI, "obsolete"
            )

            const val COMMON_THREAD = 0
            const val BROADCAST_THREAD = 1
        }
    }

    /**
     * Contains all MMS messages.
     */
    class Mms : BaseMmsColumns {
        class Inbox : BaseMmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://mms/inbox")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Sent : BaseMmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://mms/sent")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Draft : BaseMmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://mms/drafts")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Outbox : BaseMmsColumns {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.parse("content://mms/outbox")
                const val DEFAULT_SORT_ORDER = "date DESC"
            }
        }

        class Addr : BaseColumns {
            companion object {
                const val _ID = BaseColumns._ID
                const val _COUNT = BaseColumns._COUNT
                const val MSG_ID = "msg_id"
                const val CONTACT_ID = "contact_id"
                const val ADDRESS = "address"
                const val TYPE = "type"
                const val CHARSET = "charset"
            }
        }

        class Part : BaseColumns {
            companion object {
                const val _ID = BaseColumns._ID
                const val _COUNT = BaseColumns._COUNT
                const val MSG_ID = "mid"
                const val SEQ = "seq"
                const val CONTENT_TYPE = "ct"
                const val NAME = "name"
                const val CHARSET = "chset"
                const val FILENAME = "fn"
                const val CONTENT_DISPOSITION = "cd"
                const val CONTENT_ID = "cid"
                const val CONTENT_LOCATION = "cl"
                const val CT_START = "ctt_s"
                const val CT_TYPE = "ctt_t"
                const val _DATA = "_data"
                const val TEXT = "text"
            }
        }

        class Rate {
            companion object {
                @JvmField
                val CONTENT_URI: Uri = Uri.withAppendedPath(Mms.CONTENT_URI, "rate")
                const val SENT_TIME = "sent_time"
            }
        }

        class Intents private constructor() {
            companion object {
                const val EXTRA_CONTENTS = "contents"
                const val EXTRA_TYPES = "types"
                const val EXTRA_CC = "cc"
                const val EXTRA_BCC = "bcc"
                const val EXTRA_SUBJECT = "subject"
                const val CONTENT_CHANGED_ACTION = "android.intent.action.CONTENT_CHANGED"
                const val DELETED_CONTENTS = "deleted_contents"
            }
        }

        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT

            const val MESSAGE_BOX_ALL = BaseMmsColumns.MESSAGE_BOX_ALL
            const val MESSAGE_BOX_INBOX = BaseMmsColumns.MESSAGE_BOX_INBOX
            const val MESSAGE_BOX_SENT = BaseMmsColumns.MESSAGE_BOX_SENT
            const val MESSAGE_BOX_DRAFTS = BaseMmsColumns.MESSAGE_BOX_DRAFTS
            const val MESSAGE_BOX_OUTBOX = BaseMmsColumns.MESSAGE_BOX_OUTBOX

            const val DATE = BaseMmsColumns.DATE
            const val DATE_SENT = BaseMmsColumns.DATE_SENT
            const val MESSAGE_BOX = BaseMmsColumns.MESSAGE_BOX
            const val READ = BaseMmsColumns.READ
            const val SEEN = BaseMmsColumns.SEEN
            const val MESSAGE_ID = BaseMmsColumns.MESSAGE_ID
            const val SUBJECT = BaseMmsColumns.SUBJECT
            const val SUBJECT_CHARSET = BaseMmsColumns.SUBJECT_CHARSET
            const val CONTENT_TYPE = BaseMmsColumns.CONTENT_TYPE
            const val CONTENT_LOCATION = BaseMmsColumns.CONTENT_LOCATION
            const val FROM = BaseMmsColumns.FROM
            const val TO = BaseMmsColumns.TO
            const val CC = BaseMmsColumns.CC
            const val BCC = BaseMmsColumns.BCC
            const val EXPIRY = BaseMmsColumns.EXPIRY
            const val MESSAGE_CLASS = BaseMmsColumns.MESSAGE_CLASS
            const val MESSAGE_TYPE = BaseMmsColumns.MESSAGE_TYPE
            const val MMS_VERSION = BaseMmsColumns.MMS_VERSION
            const val MESSAGE_SIZE = BaseMmsColumns.MESSAGE_SIZE
            const val PRIORITY = BaseMmsColumns.PRIORITY
            const val READ_REPORT = BaseMmsColumns.READ_REPORT
            const val REPORT_ALLOWED = BaseMmsColumns.REPORT_ALLOWED
            const val RESPONSE_STATUS = BaseMmsColumns.RESPONSE_STATUS
            const val STATUS = BaseMmsColumns.STATUS
            const val TRANSACTION_ID = BaseMmsColumns.TRANSACTION_ID
            const val RETRIEVE_STATUS = BaseMmsColumns.RETRIEVE_STATUS
            const val RETRIEVE_TEXT = BaseMmsColumns.RETRIEVE_TEXT
            const val RETRIEVE_TEXT_CHARSET = BaseMmsColumns.RETRIEVE_TEXT_CHARSET
            const val READ_STATUS = BaseMmsColumns.READ_STATUS
            const val CONTENT_CLASS = BaseMmsColumns.CONTENT_CLASS
            const val DELIVERY_REPORT = BaseMmsColumns.DELIVERY_REPORT
            const val DELIVERY_TIME_TOKEN = BaseMmsColumns.DELIVERY_TIME_TOKEN
            const val DELIVERY_TIME = BaseMmsColumns.DELIVERY_TIME
            const val RESPONSE_TEXT = BaseMmsColumns.RESPONSE_TEXT
            const val SENDER_VISIBILITY = BaseMmsColumns.SENDER_VISIBILITY
            const val REPLY_CHARGING = BaseMmsColumns.REPLY_CHARGING
            const val REPLY_CHARGING_DEADLINE_TOKEN = BaseMmsColumns.REPLY_CHARGING_DEADLINE_TOKEN
            const val REPLY_CHARGING_DEADLINE = BaseMmsColumns.REPLY_CHARGING_DEADLINE
            const val REPLY_CHARGING_ID = BaseMmsColumns.REPLY_CHARGING_ID
            const val REPLY_CHARGING_SIZE = BaseMmsColumns.REPLY_CHARGING_SIZE
            const val PREVIOUSLY_SENT_BY = BaseMmsColumns.PREVIOUSLY_SENT_BY
            const val PREVIOUSLY_SENT_DATE = BaseMmsColumns.PREVIOUSLY_SENT_DATE
            const val STORE = BaseMmsColumns.STORE
            const val MM_STATE = BaseMmsColumns.MM_STATE
            const val MM_FLAGS_TOKEN = BaseMmsColumns.MM_FLAGS_TOKEN
            const val MM_FLAGS = BaseMmsColumns.MM_FLAGS
            const val STORE_STATUS = BaseMmsColumns.STORE_STATUS
            const val STORE_STATUS_TEXT = BaseMmsColumns.STORE_STATUS_TEXT
            const val STORED = BaseMmsColumns.STORED
            const val TOTALS = BaseMmsColumns.TOTALS
            const val MBOX_TOTALS = BaseMmsColumns.MBOX_TOTALS
            const val MBOX_TOTALS_TOKEN = BaseMmsColumns.MBOX_TOTALS_TOKEN
            const val QUOTAS = BaseMmsColumns.QUOTAS
            const val MBOX_QUOTAS = BaseMmsColumns.MBOX_QUOTAS
            const val MBOX_QUOTAS_TOKEN = BaseMmsColumns.MBOX_QUOTAS_TOKEN
            const val MESSAGE_COUNT = BaseMmsColumns.MESSAGE_COUNT
            const val START = BaseMmsColumns.START
            const val DISTRIBUTION_INDICATOR = BaseMmsColumns.DISTRIBUTION_INDICATOR
            const val ELEMENT_DESCRIPTOR = BaseMmsColumns.ELEMENT_DESCRIPTOR
            const val LIMIT = BaseMmsColumns.LIMIT
            const val RECOMMENDED_RETRIEVAL_MODE = BaseMmsColumns.RECOMMENDED_RETRIEVAL_MODE
            const val RECOMMENDED_RETRIEVAL_MODE_TEXT =
                BaseMmsColumns.RECOMMENDED_RETRIEVAL_MODE_TEXT
            const val STATUS_TEXT = BaseMmsColumns.STATUS_TEXT
            const val APPLIC_ID = BaseMmsColumns.APPLIC_ID
            const val REPLY_APPLIC_ID = BaseMmsColumns.REPLY_APPLIC_ID
            const val AUX_APPLIC_ID = BaseMmsColumns.AUX_APPLIC_ID
            const val DRM_CONTENT = BaseMmsColumns.DRM_CONTENT
            const val ADAPTATION_ALLOWED = BaseMmsColumns.ADAPTATION_ALLOWED
            const val REPLACE_ID = BaseMmsColumns.REPLACE_ID
            const val CANCEL_ID = BaseMmsColumns.CANCEL_ID
            const val CANCEL_STATUS = BaseMmsColumns.CANCEL_STATUS
            const val THREAD_ID = BaseMmsColumns.THREAD_ID
            const val LOCKED = BaseMmsColumns.LOCKED
            const val META_DATA = BaseMmsColumns.META_DATA

            @JvmField
            val CONTENT_URI: Uri = Uri.parse("content://mms")

            @JvmField
            val REPORT_REQUEST_URI: Uri = Uri.withAppendedPath(CONTENT_URI, "report-request")

            @JvmField
            val REPORT_STATUS_URI: Uri = Uri.withAppendedPath(CONTENT_URI, "report-status")

            const val DEFAULT_SORT_ORDER = "date DESC"

            @JvmField
            val NAME_ADDR_EMAIL_PATTERN: Pattern =
                Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*")

            @JvmField
            val QUOTED_STRING_PATTERN: Pattern =
                Pattern.compile("\\s*\"([^\"]*)\"\\s*")

            @JvmStatic
            fun getMessageBoxName(msgBox: Int): String {
                return when (msgBox) {
                    MESSAGE_BOX_ALL -> "all"
                    MESSAGE_BOX_INBOX -> "inbox"
                    MESSAGE_BOX_SENT -> "sent"
                    MESSAGE_BOX_DRAFTS -> "drafts"
                    MESSAGE_BOX_OUTBOX -> "outbox"
                    else -> throw IllegalArgumentException("Invalid message box: $msgBox")
                }
            }

            @JvmStatic
            fun extractAddrSpec(address: String): String {
                val match = NAME_ADDR_EMAIL_PATTERN.matcher(address)
                if (match.matches()) {
                    return match.group(2)
                }
                return address
            }

            @JvmStatic
            fun isEmailAddress(address: String?): Boolean {
                if (TextUtils.isEmpty(address)) {
                    return false
                }
                val s = extractAddrSpec(address!!)
                val match = Patterns.EMAIL_ADDRESS.matcher(s)
                return match.matches()
            }

            @JvmStatic
            fun isPhoneNumber(number: String?): Boolean {
                if (TextUtils.isEmpty(number)) {
                    return false
                }
                val match = Patterns.PHONE.matcher(number!!)
                return match.matches()
            }
        }
    }

    /**
     * Contains all MMS and SMS messages.
     */
    class MmsSms : BaseColumns {
        class PendingMessages : BaseColumns {
            companion object {
                const val _ID = BaseColumns._ID
                const val _COUNT = BaseColumns._COUNT

                @JvmField
                val CONTENT_URI: Uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "pending")
                const val PROTO_TYPE = "proto_type"
                const val MSG_ID = "msg_id"
                const val MSG_TYPE = "msg_type"
                const val ERROR_TYPE = "err_type"
                const val ERROR_CODE = "err_code"
                const val RETRY_INDEX = "retry_index"
                const val DUE_TIME = "due_time"
                const val LAST_TRY = "last_try"
            }
        }

        class WordsTable {
            companion object {
                const val ID = "_id"
                const val SOURCE_ROW_ID = "source_id"
                const val TABLE_ID = "table_to_use"
                const val INDEXED_TEXT = "index_text"
            }
        }

        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT

            const val TYPE_DISCRIMINATOR_COLUMN = "transport_type"

            @JvmField
            val CONTENT_URI: Uri = Uri.parse("content://mms-sms/")

            @JvmField
            val CONTENT_CONVERSATIONS_URI: Uri = Uri.parse("content://mms-sms/conversations")

            @JvmField
            val CONTENT_FILTER_BYPHONE_URI: Uri = Uri.parse("content://mms-sms/messages/byphone")

            @JvmField
            val CONTENT_UNDELIVERED_URI: Uri = Uri.parse("content://mms-sms/undelivered")

            @JvmField
            val CONTENT_DRAFT_URI: Uri = Uri.parse("content://mms-sms/draft")

            @JvmField
            val CONTENT_LOCKED_URI: Uri = Uri.parse("content://mms-sms/locked")

            @JvmField
            val SEARCH_URI: Uri = Uri.parse("content://mms-sms/search")

            const val SMS_PROTO = 0
            const val MMS_PROTO = 1

            const val NO_ERROR = 0
            const val ERR_TYPE_GENERIC = 1
            const val ERR_TYPE_SMS_PROTO_TRANSIENT = 2
            const val ERR_TYPE_MMS_PROTO_TRANSIENT = 3
            const val ERR_TYPE_TRANSPORT_FAILURE = 4
            const val ERR_TYPE_GENERIC_PERMANENT = 10
            const val ERR_TYPE_SMS_PROTO_PERMANENT = 11
            const val ERR_TYPE_MMS_PROTO_PERMANENT = 12
        }
    }

    class Carriers : BaseColumns {
        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT

            @JvmField
            val CONTENT_URI: Uri = Uri.parse("content://telephony/carriers")
            const val DEFAULT_SORT_ORDER = "name ASC"
            const val NAME = "name"
            const val APN = "apn"
            const val PROXY = "proxy"
            const val PORT = "port"
            const val MMSPROXY = "mmsproxy"
            const val MMSPORT = "mmsport"
            const val SERVER = "server"
            const val USER = "user"
            const val PASSWORD = "password"
            const val MMSC = "mmsc"
            const val MCC = "mcc"
            const val MNC = "mnc"
            const val NUMERIC = "numeric"
            const val AUTH_TYPE = "authtype"
            const val TYPE = "type"
            const val INACTIVE_TIMER = "inactivetimer"
            const val ENABLED = "enabled"
            const val CLASS = "class"
            const val PROTOCOL = "protocol"
            const val ROAMING_PROTOCOL = "roaming_protocol"
            const val CURRENT = "current"
            const val CARRIER_ENABLED = "carrier_enabled"
            const val BEARER = "bearer"
        }
    }

    /**
     * Contains received SMS cell broadcast messages.
     */
    class CellBroadcasts private constructor() : BaseColumns {
        companion object {
            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT

            @JvmField
            val CONTENT_URI: Uri = Uri.parse("content://cellbroadcasts")

            const val GEOGRAPHICAL_SCOPE = "geo_scope"
            const val SERIAL_NUMBER = "serial_number"
            const val PLMN = "plmn"
            const val LAC = "lac"
            const val CID = "cid"
            const val V1_MESSAGE_CODE = "message_code"
            const val V1_MESSAGE_IDENTIFIER = "message_id"
            const val SERVICE_CATEGORY = "service_category"
            const val LANGUAGE_CODE = "language"
            const val MESSAGE_BODY = "body"
            const val DELIVERY_TIME = "date"
            const val MESSAGE_READ = "read"
            const val MESSAGE_FORMAT = "format"
            const val MESSAGE_PRIORITY = "priority"
            const val ETWS_WARNING_TYPE = "etws_warning_type"
            const val CMAS_MESSAGE_CLASS = "cmas_message_class"
            const val CMAS_CATEGORY = "cmas_category"
            const val CMAS_RESPONSE_TYPE = "cmas_response_type"
            const val CMAS_SEVERITY = "cmas_severity"
            const val CMAS_URGENCY = "cmas_urgency"
            const val CMAS_CERTAINTY = "cmas_certainty"
            const val DEFAULT_SORT_ORDER = "$DELIVERY_TIME DESC"

            @JvmField
            val QUERY_COLUMNS = arrayOf(
                _ID,
                GEOGRAPHICAL_SCOPE,
                PLMN,
                LAC,
                CID,
                SERIAL_NUMBER,
                SERVICE_CATEGORY,
                LANGUAGE_CODE,
                MESSAGE_BODY,
                DELIVERY_TIME,
                MESSAGE_READ,
                MESSAGE_FORMAT,
                MESSAGE_PRIORITY,
                ETWS_WARNING_TYPE,
                CMAS_MESSAGE_CLASS,
                CMAS_CATEGORY,
                CMAS_RESPONSE_TYPE,
                CMAS_SEVERITY,
                CMAS_URGENCY,
                CMAS_CERTAINTY
            )
        }
    }

    class Intents private constructor() {
        companion object {
            const val SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE"
            const val SPN_STRINGS_UPDATED_ACTION =
                "android.provider.Telephony.SPN_STRINGS_UPDATED"
            const val EXTRA_SHOW_PLMN = "showPlmn"
            const val EXTRA_PLMN = "plmn"
            const val EXTRA_SHOW_SPN = "showSpn"
            const val EXTRA_SPN = "spn"
        }
    }
}
