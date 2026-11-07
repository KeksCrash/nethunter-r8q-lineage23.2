package de.nethunter.flippernfc;
/** One explicit start, automatic bind -> independent verification, bounded retries. */
final class SetupSequence {
 enum Step { BIND, VERIFY }
 enum Result { WAIT_FOR_NEXT_TAP, DONE, STOPPED }
 private boolean active;
 private int failures;
 synchronized void start(){active=true;failures=0;}
 synchronized void cancel(){active=false;}
 synchronized boolean active(){return active;}
 synchronized Step next(boolean bound){if(!active)throw new IllegalStateException("SETUP_NOT_ACTIVE");return bound?Step.VERIFY:Step.BIND;}
 synchronized Result complete(Step step,boolean bound,boolean enabled){
  if(!active)return Result.STOPPED;
  if(step==Step.VERIFY&&bound&&enabled){active=false;return Result.DONE;}
  if(step==Step.BIND&&bound)return Result.WAIT_FOR_NEXT_TAP;
  active=false;return Result.STOPPED;
 }
 synchronized Result transportFailed(){
  if(!active)return Result.STOPPED;
  if(++failures<3)return Result.WAIT_FOR_NEXT_TAP;
  active=false;return Result.STOPPED;
 }
}
