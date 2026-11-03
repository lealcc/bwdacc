package com.leal.comfyremote;
import java.util.*;
public class CharacterMentionsTest {
 static void check(boolean test,String label){if(!test)throw new AssertionError(label);}
 public static void main(String[] args){
  Map<String,String> people=new HashMap<>();people.put("ana_lucia","woman in a blue coat");people.put("leo","man holding $5 \\ bag and @ana_lucia");
  check(CharacterMentions.handle("Ana Lúcia").equals("ana_lucia"),"accent and space handle");
  check(CharacterMentions.expand("Photo of @ANA_LUCIA, beside @leo.",people).equals("Photo of woman in a blue coat, beside man holding $5 \\ bag and @ana_lucia."),"multiple, case, punctuation, no recursive expansion");
  check(CharacterMentions.expand("mail@leo.com @unknown @leo2",people).equals("mail@leo.com @unknown @leo2"),"email and unknown not replaced");
  check(CharacterMentions.start("Photo @ana",10)==6,"partial mention detection");
  check(CharacterMentions.start("Photo @ana ",11)==-1,"completed mention hidden");
  check(CharacterMentions.contains("@ana_lucia",people)&&!CharacterMentions.contains("@unknown",people),"legacy selection precedence");
  System.out.println("PASS: character mentions");
 }
}
