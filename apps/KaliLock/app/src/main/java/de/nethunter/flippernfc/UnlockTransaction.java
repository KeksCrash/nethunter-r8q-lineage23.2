package de.nethunter.flippernfc;
import android.os.SystemClock;
/** A cancelled/timed-out NFC callback must never deliver a late credential. */
final class UnlockTransaction {
 private static final ThreadLocal<UnlockTransaction> CURRENT=new ThreadLocal<>();
 private static final java.util.concurrent.atomic.AtomicLong EPOCH=new java.util.concurrent.atomic.AtomicLong();
 private final long epoch=EPOCH.get();
 private final long until=SystemClock.elapsedRealtime()+19000;
 private volatile boolean cancelled,verified;
 private boolean delivered;
 static void enter(UnlockTransaction t){CURRENT.set(t);}
 static void leave(){CURRENT.remove();}
 void cancel(){cancelled=true;}
 static void cancelAll(){EPOCH.incrementAndGet();}
 boolean valid(){return !cancelled&&epoch==EPOCH.get()&&SystemClock.elapsedRealtime()<until;}
 void markVerified(){check();verified=true;}
 void check(){if(!valid()||Thread.currentThread().isInterrupted())throw new IllegalStateException("NFC_TRANSACTION_EXPIRED");}
 static UnlockTransaction current(){UnlockTransaction t=CURRENT.get();if(t==null)throw new IllegalStateException("NO_FRESH_NFC_TRANSACTION");t.check();return t;}
 synchronized void authorizeDelivery(){check();if(!verified||delivered)throw new IllegalStateException("NO_FRESH_VERIFIED_ASSERTION");delivered=true;}
}
