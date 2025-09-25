#include <jni.h>
#include <string>

//static std::string apiBaseUrlPublic = "https://zdost.aoserv.com/"; // PUBLIC URL JDW and Ashraf
static std::string apiBaseUrlPublic = "https://zd.aoserv.com/"; // PUBLIC URL RYK
static std::string API_LOGIN = "api/Account/login";
static std::string API_LOGIN_SUR = "api/Account/LoginSurveyor";
static std::string API_FORGOT_PASSWORD = "api/Account/forgetpassword";
static std::string API_OTP_VERIFICATION = "api/Account/VerifyTotp";
static std::string API_UPDATE_PASSWORD = "api/Account/updatepassword";
static std::string API_Sync_Mauza = "api/MobileData/SyncMauzaInfo";
static std::string API_Mauza_Assigned = "api/KatchiAbadi/user";
//static std::string API_SYNC_DATA = "api/KatchiAbadi/kachiabachi";
static std::string API_SYNC_DATA = "api/KatchiAbadi/kachiabachiNew";
//static std::string API_POST_SURVEY_DATA = "api/FieldRecord";
static std::string API_POST_SURVEY_DATA = "api/FieldRecord/FieldRecordNew";
static std::string API_POST_SURVEY_DATA_REVISIT = "/api/FieldRecord/FieldRecordPartialRevisit";
static std::string API_POST_SURVEY_DATA_RETAKE_PICTURES = "/api/FieldRecord/SaveReVisitedPicture";


extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiBaseUrlPublic(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(apiBaseUrlPublic.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiLogin(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_LOGIN.c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiLoginSur(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_LOGIN_SUR.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiForgotPassword(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_FORGOT_PASSWORD.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiOtpVerification(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_OTP_VERIFICATION.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiUpdatePassword(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_UPDATE_PASSWORD.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiMauzaAssigned(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_Mauza_Assigned.c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiSyncMauzaInfo(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_Sync_Mauza.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiSyncData(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_SYNC_DATA.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiPostSurveyData(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_POST_SURVEY_DATA.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiPostSurveyDataRevisit(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_POST_SURVEY_DATA_REVISIT.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_pk_gop_pulse_katchiAbadi_MyApplication_getApiPostSurveyDataRetakePictures(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(API_POST_SURVEY_DATA_RETAKE_PICTURES.c_str());
}
