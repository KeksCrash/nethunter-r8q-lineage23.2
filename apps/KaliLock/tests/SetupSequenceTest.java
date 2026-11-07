package de.nethunter.flippernfc;
public final class SetupSequenceTest {
 static int passed;static void check(boolean b){if(!b)throw new AssertionError();passed++;}
 public static void main(String[] args){
  SetupSequence flow=new SetupSequence();check(!flow.active());flow.start();
  check(flow.next(false)==SetupSequence.Step.BIND);
  check(flow.complete(SetupSequence.Step.BIND,true,false)==SetupSequence.Result.WAIT_FOR_NEXT_TAP);
  check(flow.active());check(flow.next(true)==SetupSequence.Step.VERIFY);
  check(flow.complete(SetupSequence.Step.VERIFY,true,true)==SetupSequence.Result.DONE);check(!flow.active());
  flow.start();check(flow.complete(SetupSequence.Step.VERIFY,true,false)==SetupSequence.Result.STOPPED);check(!flow.active());
  flow.start();check(flow.complete(SetupSequence.Step.BIND,false,false)==SetupSequence.Result.STOPPED);
  flow.start();check(flow.transportFailed()==SetupSequence.Result.WAIT_FOR_NEXT_TAP);check(flow.transportFailed()==SetupSequence.Result.WAIT_FOR_NEXT_TAP);check(flow.transportFailed()==SetupSequence.Result.STOPPED);check(!flow.active());
  flow.start();flow.cancel();check(flow.complete(SetupSequence.Step.VERIFY,true,true)==SetupSequence.Result.STOPPED);
  check(flow.transportFailed()==SetupSequence.Result.STOPPED);
  System.out.println("PASS "+passed+" one-action sequencing/cancellation/retry/false-success checks");
 }
}
