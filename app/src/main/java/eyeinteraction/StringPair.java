package eyeinteraction;

/** StringPair - A simple clase to hold a pair of
* strings.
*
* Used by the <code>MSD</code> and <code>ErrorMatrix</code> classes.  
*/
public class StringPair
{
   public String s1;
   public String s2;

   public StringPair() { s1 = ""; s2 = ""; };

   public void CopyConcat(StringPair p, char c1, char c2)
      { s1 = p.s1 + c1; s2 = p.s2 + c2; };
} 
