package de.nethunter.flippernfc;
import android.os.SystemClock;
public final class UnlockTransactionTest {
 static int passed;
 static void check(boolean b){if(!b)throw new AssertionError();passed++;}
 static void rejects(Runnable r){try{r.run();}catch(IllegalStateException expected){passed++;return;}throw new AssertionError("Late/unverified transaction accepted");}
 public static void main(String[] args){
  SystemClock.now=1000;
  rejects(UnlockTransaction::current);
  UnlockTransaction t=new UnlockTransaction();UnlockTransaction.enter(t);
  check(t.valid());rejects(t::authorizeDelivery);t.markVerified();t.authorizeDelivery();rejects(t::authorizeDelivery);
  UnlockTransaction.leave();rejects(UnlockTransaction::current);
  t=new UnlockTransaction();t.markVerified();SystemClock.now+=19000;check(!t.valid());rejects(t::authorizeDelivery);
  t=new UnlockTransaction();t.markVerified();t.cancel();check(!t.valid());rejects(t::authorizeDelivery);
  t=new UnlockTransaction();t.markVerified();UnlockTransaction.cancelAll();check(!t.valid());rejects(t::authorizeDelivery);
  t=new UnlockTransaction();Thread.currentThread().interrupt();rejects(t::markVerified);Thread.interrupted();
  System.out.println("PASS "+passed+" transaction deadline/cancellation/reuse checks");
 }
}
