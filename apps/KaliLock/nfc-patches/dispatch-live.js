'use strict';
// Reversibler Test: Nur innerhalb dispatchTag darf OFF_LOCKED als ON_LOCKED gelten.
// Alle Authentifizierungsresultate und die Android-Gerätesperre bleiben unverändert.
Java.perform(function(){
 const scoped=new Set();
 const H=Java.use('com.android.nfc.ScreenStateHelper');
 const check=H.checkScreenState.overload('boolean');
 check.implementation=function(flag){const state=check.call(this,flag); if(state===2&&scoped.has(Process.getCurrentThreadId())){console.log(Date.now()+' DISPATCH_SCREEN 2->4');return 4;}return state;};
 const D=Java.use('com.android.nfc.NfcDispatcher');const dispatch=D.dispatchTag.overload('android.nfc.Tag');
 dispatch.implementation=function(tag){const tid=Process.getCurrentThreadId();scoped.add(tid);try{console.log(Date.now()+' DISPATCH_BEGIN tech='+tag.getTechList());const result=dispatch.call(this,tag);console.log(Date.now()+' DISPATCH_END result='+result);return result;}finally{scoped.delete(tid);}};
 const U=Java.use('com.android.nfc.NfcUnlockManager');const attempt=U.tryUnlock.overload('android.nfc.Tag');
 attempt.implementation=function(tag){console.log(Date.now()+' UNLOCK_CALLBACK_BEGIN');const result=attempt.call(this,tag);console.log(Date.now()+' UNLOCK_CALLBACK_END result='+result);return result;};
 console.log('DISPATCH_TEST_READY: temporaer, kein permanenter Patch');
});
