package org.duofold.live;

/** The two frozen orientations seen in a single worker check, in logical display order. */
final class FoldRotationProof {
 final long session, observed;
 final String primary, secondary;
 final int firstRotation, secondRotation;
 FoldRotationProof(long session,long observed,String primary,int firstRotation,String secondary,int secondRotation) {
  this.session=session;this.observed=observed;this.primary=primary;this.secondary=secondary;
  this.firstRotation=firstRotation;this.secondRotation=secondRotation;
 }
 boolean matches(long token,long now,String first,int firstAngle,String second,int secondAngle) {
  return token>0 && session==token && observed>=token && now>=observed && now-observed<=250
    && primary.equals(first) && secondary.equals(second) && !primary.equals(secondary)
    && firstRotation==firstAngle && secondRotation==secondAngle;
 }
}
