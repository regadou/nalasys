package org.regadou.nalasys;

import java.util.*;
import java.net.*;
import java.io.*;

public class Expression implements Map.Entry {

   private static final int checkNumber=0, checkDate=1, checkAction=2, useDictio=3, useUri=4, useDatabase=5, useContext=6;
   private static final String[] checkNames = "".split(",");
   public static int[] checkOrder = {checkNumber,checkDate,checkAction,useDictio,useUri,useDatabase,useContext};
   private static final int WORD_STATUS=0, POS_STATUS=1, STOP_STATUS=2;
   private static final Action NOOP = new Action() {
      private Class[] params = new Class[0];
      public Class[] parameters() {
         return params;
      }
      public Object execute(Object target, Object[] params) throws Exception {
         return null;
      }
   };

   private final Context cx = Context.currentContext();
   private int apos = 0;
   private String spliters = ",;!?.'`";
   private String groupers = "()[]{}";
   private String escapes = "\"";
   private String operators = "+-*/%^\\:=<>~|&@|#";
   private String multiEnter = ".";

	private List tokens;
	private Action action;
	private Object first;
	private Object last;

	public Expression() {
		this.tokens = new ArrayList();
	}

	public Expression(Object[] tokens) {
		this.tokens = (tokens == null) ? new ArrayList() : new ArrayList(Arrays.asList(tokens));
	}

	public Expression(Collection tokens) {
		this.tokens = (tokens == null) ? new ArrayList()
                  : ((tokens instanceof List) ? (List)tokens : new ArrayList(tokens));
	}

	public Expression(String txt) {
      tokens = new ArrayList();
		if (txt != null)
         parseArray(tokens, txt.toCharArray(), new int[]{-1, 0, 0});
	}

	public Action getAction() {
      compile();
		return action;
	}

	public Object getFirst() {
      compile();
		return first;
	}

	public Object getLast() {
      compile();
		return last;
	}

	public Object[] getTokens() {
		return tokens.toArray();
	}

	public boolean addToken(Object token) {
		if (isCompiled() || token == null)
			return false;
		tokens.add(token);
		return true;
	}

	public boolean isCompiled() {
		return action != null;
	}

	public boolean compile() {
		if (action != null)
			return true;
		try {
			Action act = null;
			Object p1 = null;
			Object p2 = null;

			for (Object value : tokens) {
            if (value instanceof Action) {
					Action a = (Action)value;
					if (act == null)
						act = a;
					else if (p1 != null && p2 == null)
						p2 = a;
					else if (a.getPrecedence() <= act.getPrecedence()) {
						Expression exp = new Expression();
						exp.action = act;
						exp.first = p1;
						exp.last = p2;
						act = a;
						p1 = exp;
						p2 = null;
					}
					else if (p2 instanceof Expression)
						((Expression)p2).addToken(a);
					else
						p2 = new Expression(new Object[]{p2, a});
				}
				else {
					if (value instanceof Collection)
						value = new Expression((Collection)value);

					if (act == null)
						p1 = setParam(p1, value);
					else if (p2 instanceof Expression)
						((Expression)p2).addToken(value);
					else
						p2 = setParam(p2, value);
				}
			}

			action = (act == null) ? NOOP : act;
			first = p1;
			last = p2;
			return true;
		}
		catch (Exception e) {
			Context.exception(e, "Expression.compile()");
			return false;
		}
	}

   public Object getKey() {
      return tokens;
   }

	public Object getValue() {
		return getValue(null);
	}

	public Object getValue(Object[] params) {
	// comment passer les parametres a l'expression
	// peut-etre avec une chaine de dictios
		if (action == null && !compile())
			return null;
      try { return (action == NOOP) ? evalParams() : action.execute(null, new Object[]{first, last}); }
      catch (Exception e) { return Context.exception(e, "Expression.execute("+params+")"); }
	}

   public Object setValue(Object o) {
      throw new UnsupportedOperationException("Expression does not support setting its value");
   }

   @org.codehaus.jackson.annotate.JsonValue
	public String toString() {
      if(action == null)
         return Converter.toString(tokens);
      else if (last == null)
         return (first == null) ? action.toString() : "("+first+" "+action+")";
      else if (first == null)
         return "("+action+" "+last+")";
      else
         return "("+first+" "+action+" "+last+")";
	}

	private Object setParam(Object old, Object token) {
		if (old == null)
			return token;
		else if (old instanceof Collection) {
			((Collection)old).add(token);
			return old;
		}
		else {
			List lst = new ArrayList();
         lst.add(old);
         lst.add(token);
         return lst;
      }
	}

   private Object evalParams() {
      if (first == null)
         return (last == null) ? null : last;
      else if (last == null)
         return first;
      else {
         Collection p = new ArrayList();
         p.addAll(Converter.toCollection(first));
         p.addAll(Converter.toCollection(last));
         return p;
      }
   }

   private void parseArray(List tokens, char[] chars, int[] status) {
      int stop = status[STOP_STATUS];
      boolean isop = false;
      for (; status[POS_STATUS] < chars.length; status[POS_STATUS]++) {
         char c = chars[status[POS_STATUS]];
         if (c == stop) {
            stop = 0;
            break;
         }

         int p = spliters.indexOf(c);
         if (p >= 0) {
            switch (c) {
               case '.':
                  if (status[WORD_STATUS] >= 0 && status[POS_STATUS] < chars.length-1 && chars[status[POS_STATUS]+1] > ' ')
                     p = -1;
                  break;
               case '\'':
               case '`':
                  c = (char)((apos < 0) ? 0 : apos+1);
            }
         }
         if (p < 0) {
            p = groupers.indexOf(c);
            if (p < 0) {
               p = operators.indexOf(c);
               if (p >= 0) {
                  switch (c) {
                     case '@':
                     case '.':
                     case ':':
                        if (status[WORD_STATUS] >= 0 && status[POS_STATUS] < chars.length-1 && chars[status[POS_STATUS]+1] > ' ')
                           p = -1;
                        break;
                     case '-':
                        if (status[WORD_STATUS] >= 0 && Character.isLetter(chars[status[POS_STATUS]-1])
                           && status[POS_STATUS] < chars.length-1 && Character.isLetter(chars[status[POS_STATUS]+1]))
                           p = -1;
                  }
               }
               if (p < 0) {
                  p = escapes.indexOf(c);
                  if (p >= 0) {
                     status[POS_STATUS]++;
                     status[STOP_STATUS] = c;
                     addToken(tokens, parseToken(parseString(chars, status)));
                  }
                  else if (c == '\n' && multiEnter != null && !tokens.isEmpty()
                        && !Action.isAction(tokens.get(tokens.size() - 1))) {
                     addToken(tokens, "\n");
                     break;
                  }
                  else if (c <= 32) {
                     if (status[WORD_STATUS] >= 0) {
                        String txt = new String(chars, status[WORD_STATUS], status[POS_STATUS]-status[WORD_STATUS]);
                        addToken(tokens, parseToken(txt));
                        status[WORD_STATUS] = -1;
                     }
                  }
                  else if (status[WORD_STATUS] < 0)
                     status[WORD_STATUS] = status[POS_STATUS];
                  else if (isop) {
                     String txt = new String(chars, status[WORD_STATUS], status[POS_STATUS]-status[WORD_STATUS]);
                     addToken(tokens, parseToken(txt));
                     status[WORD_STATUS] = status[POS_STATUS];
                     isop = false;
                  }
               }
               else if (status[WORD_STATUS] >= 0 && isop)
                  ;
               else {
                  if (status[WORD_STATUS] >= 0) {
                     String txt = new String(chars, status[WORD_STATUS], status[POS_STATUS]-status[WORD_STATUS]);
                     addToken(tokens, parseToken(txt));
                  }
                  status[WORD_STATUS] = status[POS_STATUS];
                  isop = true;
               }
            }
            else if (p % 2 == 1)
               throw new RuntimeException("Misplaced grouping character "+groupers.charAt(p));
            else {
               status[POS_STATUS]++;
					status[STOP_STATUS] = groupers.charAt(p+1);
               List sub = new ArrayList();
               parseArray(sub, chars, status);
               addToken(tokens, new Expression(sub));
            }
         }
         else {
            if (status[WORD_STATUS] >= 0) {
               int keep = (c == 0) ? 1 : 0;
               String txt = new String(chars, status[WORD_STATUS], status[POS_STATUS]-status[WORD_STATUS]+keep);
               addToken(tokens, parseToken(txt));
            }
            status[WORD_STATUS] = (c > 1 && c <= ' ') ? status[POS_STATUS] : -1;
            isop = false;
            if (c > ' ')
               addToken(tokens, parseToken(c+""));
         }
		}

		if (stop != 0)
			throw new RuntimeException("Missing grouping character "+((char)stop));
      else if(status[WORD_STATUS] >= 0) {
         String txt = new String(chars, status[WORD_STATUS], status[POS_STATUS]-status[WORD_STATUS]);
         addToken(tokens, parseToken(txt));
      }
	}

   private String parseString(char[] chars, int[] status) {
      int stop = status[STOP_STATUS];

      for (int start = status[POS_STATUS]; status[POS_STATUS] < chars.length; status[POS_STATUS]++) {
          if (chars[status[POS_STATUS]] == stop)
             return new String(chars, start, status[POS_STATUS]-start);
      }

      throw new RuntimeException("Missing end of text character "+((char)stop));
   }

   private Object parseToken(String txt) {
      if (txt == null || txt.trim().equals(""))
         return null;

      boolean usingDictio = false;
      for (int type : checkOrder) {
         Object val = null;
         switch (type) {
            case checkNumber:
               val = Converter.toNumber(txt);
               break;
            case checkDate:
                  val = Converter.toDate(txt);
               break;
            case checkAction:
                  val = Operator.getOperator(txt);
               break;
            case useDictio:
                  val = cx.getAttributeEntry(txt);
              break;
            case useUri:
               val = Converter.toUri(txt);
               break;
            case useDatabase:
               Database db = cx.getDatabase();
               if (db != null) {
                  try { val = db.getColumns(txt); }
                  catch (Exception e) {}
               }
               break;
            case useContext:
               val = new Entity(cx).get(txt);
         }
         if (val != null)
            return val;
      }

      if (usingDictio) {
         cx.setAttribute(txt, null);
         return cx.getAttributeEntry(txt);
      }
      else
         return new Property(null, txt);
   }

   private void addToken(List tokens, Object obj) {
      if (multiEnter != null && !tokens.isEmpty()) {
         Object last = tokens.get(tokens.size()-1);
         if (last.toString().equals("\n")) {
            tokens.remove(tokens.size()-1);
            if (!Action.isAction(obj)) {
               Object val = parseToken(multiEnter);
               if (val != null)
                  tokens.add(val);
            }
         }
      }
      tokens.add(obj);
   }
}



