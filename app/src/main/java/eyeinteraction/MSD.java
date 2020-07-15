package eyeinteraction;

/**
 * MSD -- Minimum String Distance -- a class to generate various statistics related to the lexical distance between two
 * strings. Includes a main method as a demonstration.
 * <p>
 * 
 * Related references include the following:
 * <p>
 * 
 * <ul>
 * <li><a href="http:www.yorku.ca/mack/CHI01a.htm"> Measuring errors in text entry tasks: An application of the
 * Levenshtein string distance statistic, </a> by Soukoreff and MacKenzie (<i>CHI 2002</i>). This is the paper that
 * first introduced the minimum string distance (MSD) method for calculating error rates in text entry tasks.
 * 
 * <li><a href="http:www.yorku.ca/mack/nordichi2002-shortpaper.html"> A character-level error analysis technique for
 * evaluating text entry methods </a>, by MacKenzie and Soukoreff (<i>NordiCHI 2002</i>). This paper introduced the use
 * of the "mean alignment string length" as the appropropriate denominator in computing the error rates. It also
 * demonstrates how to use the "error rate matrix" (aka "confusion matrix") in analysing the types of errors that
 * occurred.
 * </ul>
 * <p>
 * 
 * Example dialogue:
 * 
 * <pre>
*     PROMPT>java MSD ?
*     usage: java MSD [-m] [-a] [-er]
*     
*     where -m  = output the MSD matrix
*           -a  = output the set of optimal alignments
*           -er = output the error rate
*     
*     PROMPT>java MSD -m -a -er
*     ============================
*     Minimum String Distance Demo
*     ============================
*     Enter pairs of strings (^z to exit)
*     golfers
*     gofpiers
*     MSD = 3
*     Error rate (old) = 37.5000%
*     Error rate (new) = 36.3636%
*           g  o  f  p  i  e  r  s
*        0  1  2  3  4  5  6  7  8
*     g  1  0  1  2  3  4  5  6  7
*     o  2  1  0  1  2  3  4  5  6
*     l  3  2  1  1  2  3  4  5  6
*     f  4  3  2  1  2  3  4  5  6
*     e  5  4  3  2  2  3  3  4  5
*     r  6  5  4  3  3  3  4  3  4
*     s  7  6  5  4  4  4  4  4  3
*     Alignments: 4, mean size: 8.25
*     golf--ers
*     go-fpiers
*     
*     golf-ers
*     gofpiers
*     
*     gol-fers
*     gofpiers
*     
*     go-lfers
*     gofpiers
*     -------------
*  </pre>
 * 
 * @author Scott MacKenzie, 2001-2011
 * @author William Soukoreff, 2002
 ******************************************************************/
public class MSD
{
	private String s1, s2;
	private int[][] d;

	/**
	 * Create an MSD object.
	 * 
	 * @param s1Arg
	 *            the 1st text string (the "presented" text)
	 * @param s2Arg
	 *            the 2nd text string (the "transcribed" text)
	 */
	public MSD(String s1Arg, String s2Arg)
	{
		s1 = s1Arg;
		s2 = s2Arg;
		buildMatrix();
	}

	private static int r(char a, char b)
	{
		if (a == b)
			return 0;
		else
			return 1;
	}

	private void buildMatrix()
	{
		d = new int[s1.length() + 1][s2.length() + 1];
		int i, j;

		if (s1.length() == 0 || s2.length() == 0)
		{
			d[s1.length()][s2.length()] = Math.max(s1.length(), s2.length());
			return;
		}

		for (i = 0; i < s1.length() + 1; i++)
			d[i][0] = i;

		for (j = 0; j < s2.length() + 1; j++)
			d[0][j] = j;

		for (i = 1; i <= s1.length(); i++)
			for (j = 1; j <= s2.length(); j++)
			{
				int a, b, c, m;
				a = d[i - 1][j] + 1;
				b = d[i][j - 1] + 1;
				c = d[i - 1][j - 1] + r(s1.charAt(i - 1), s2.charAt(j - 1));
				m = Math.min(a, b);
				m = Math.min(m, c);
				d[i][j] = m;
			}
	}

	/**
	 * Returns the minimum string distance matrix.
	 * 
	 * The number of rows in the matrix is <code>s1.length()&nbsp;+&nbsp;1</code>. The number of columns is
	 * <code>s2.length()&nbsp;+&nbsp;1</code>. The value of the minimum string distance statistic may be retrieved from
	 * <code>msdMatrix[s1.length()][s2.length()]</code>.
	 * <p>
	 * 
	 * @return a two dimensional integer array containing the minimum string distance matrix.
	 */
	public int[][] getMatrix()
	{
		return d;
	}

	/**
	 * Return an integer equal to the minimum distance between two strings.
	 * 
	 * The minimum distance is the minimum number of primitive operations that can be applied to one string to yield the
	 * other. The primitives are insert, delete, and substitute.
	 * <p>
	 * 
	 * For details, see Soukoreff & MacKenzie (2001).
	 * <p>
	 * 
	 * @return an <code>int</code> equal to the minimum string distance.
	 */
	public int getMSD()
	{
		return d[s1.length()][s2.length()];
	}

	/**
	 * Returns the S1 string
	 */
	public String getS1()
	{
		return s1;
	}

	/**
	 * Returns the S2 string
	 */
	public String getS2()
	{
		return s2;
	}

	/**
	 * Return a double equal to the text entry error rate (%).
	 * 
	 * The error rate is computed by dividing the MSD statistic by the larger of the lengths of the presented text
	 * string and the transcribed text string, and multiplying by 100.
	 */
	public double getErrorRate()
	{
		return (double) getMSD() / Math.max(s1.length(), s2.length()) * 100.0;
	}

	@SuppressWarnings("unused")
	private void dumpMatrix()
	{
		int rows = d.length;
		int cols = d[0].length;

		String s11 = " " + s1;
		String s22 = " " + s2;

		System.out.print(" ");
		for (int k = 0; k < s22.length(); ++k)
			System.out.print("  " + s22.substring(k, k + 1));
		System.out.println();

		for (int i = 0; i < rows; ++i)
		{
			System.out.print(s11.substring(i, i + 1));
			for (int j = 0; j < cols; ++j)
			{
				String f = d[i][j] + "";
				while (f.length() < 3)
					f = " " + f;
				System.out.print(f);
			}
			if (i != rows - 1)
				System.out.println();
		}
		System.out.println();
	}

	/**
	 * Self-test main method to demonstrate the MSD class.
	 */
	/*
	 * public static void main(String[] args) throws IOException { boolean mOption = false; boolean aOption = false;
	 * boolean erOption = false;
	 * 
	 * for (int i = 0; i < args.length; ++i) { if (args[i].equals("-m")) mOption = true; else if (args[i].equals("-a"))
	 * aOption = true; else if (args[i].equals("-er")) erOption = true; else if (args[i].equals("?")) usage(); else
	 * usage(); }
	 * 
	 * BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in), 1);
	 * System.out.println("============================"); System.out.println("Minimum String Distance Demo");
	 * System.out.println("============================"); System.out.println("Enter pairs of strings (^z to exit)");
	 * 
	 * String s1, s2; while ((s1 = stdin.readLine()) != null && (s2 = stdin.readLine()) != null) { MSD s1s2 = new
	 * MSD(s1, s2);
	 * 
	 * System.out.println("MSD = " + s1s2.getMSD());
	 * 
	 * if (erOption) { System.out.println("Error rate (old) = " + MyUtil.formatDouble(s1s2.getErrorRate(), 7, 4) + "%");
	 * System.out.println("Error rate (new) = " + MyUtil.formatDouble(s1s2.getErrorRateNew(), 7, 4) + "%"); }
	 * 
	 * if (mOption) s1s2.dumpMatrix();
	 * 
	 * if (aOption) { StringPair[] sp = s1s2.getAlignments();
	 * 
	 * if (sp == null) System.out.println("Outlier!  Alignments not available"); else {
	 * System.out.println("Alignments: " + sp.length + ", " + "mean size: " + s1s2.meanAlignmentSize());
	 * 
	 * for(int i = 0; i < sp.length; i++) { if(i > 0) System.out.println(""); System.out.println(sp[i].s1);
	 * System.out.println(sp[i].s2); } System.out.println("-------------"); } } } }
	 */

	@SuppressWarnings("unused")
	private static void usage()
	{
		String usageString = "usage: java MSD [-m] [-k] [-er]\n" + "\n" + "where -m  = output the MSD matrix\n"
				+ "      -a  = output the set of optimal alignments\n" + "      -er = output the error rate";

		System.out.println(usageString);
		System.exit(0);
	}

	/*
	 * This is a helper-function used by doAlignments(). It accomplishes two things:
	 * 
	 * 1 - Two arrays of String Pairs are concatenated, forming the return array.
	 * 
	 * 2 - A character (either c1 or c2) is added to the ends of all of the first array strings, as they are copied into
	 * the return value array. The character c1 is added to all of the presented texts, and c2 to all of the transribed
	 * texts.
	 * 
	 * The second argument array is added to the result array verbatim.
	 */
	private static StringPair[] DoubleConcat(StringPair[] a, char c1, char c2, StringPair[] b)
	{
		int i;
		StringPair[] returnvalue = new StringPair[a.length + b.length];

		for (i = 0; i < a.length + b.length; i++)
			returnvalue[i] = new StringPair();

		for (i = 0; i < a.length; i++)
			returnvalue[i].CopyConcat(a[i], c1, c2);

		// use the quick array copy here...
		System.arraycopy(b, 0, returnvalue, a.length, b.length);
		// ...instead of the correct, but slower...
		// for(i = 0; i < b.length; i++)
		// returnvalue[i + a.length] = b[i];

		return returnvalue;
	}

	/*
	 * This function does the work of producing the alignment strings. It calls by itself recursively. The idea is to
	 * traverse the 'D' matrix, from bottom right to top left, recursing anywhere there is more than one path through
	 * the matrix.<p>
	 * 
	 * This function uses the <code>DoubleConcat()</code> function above, and the <code>StringPair</code> class.
	 */
	private static StringPair[] doAlignments(String s1, String s2, int[][] d, int x, int y)
	{
		StringPair[] returnarray = new StringPair[0];

		if (x == 0 && y == 0)
		{
			returnarray = new StringPair[1];
			returnarray[0] = new StringPair();
			return returnarray;
		}

		if (x > 0 && y > 0)
		{
			// Correct (matching) characters
			if (d[x][y] == d[x - 1][y - 1] && s1.charAt(x - 1) == s2.charAt(y - 1))
				returnarray = DoubleConcat(doAlignments(s1, s2, d, x - 1, y - 1), s1.charAt(x - 1), s2.charAt(y - 1),
						returnarray);

			// Substitution Error
			if (d[x][y] == d[x - 1][y - 1] + 1)
				returnarray = DoubleConcat(doAlignments(s1, s2, d, x - 1, y - 1), s1.charAt(x - 1), s2.charAt(y - 1),
						returnarray);
		}

		// Insertion Error
		if (x > 0 && d[x][y] == d[x - 1][y] + 1)
			returnarray = DoubleConcat(doAlignments(s1, s2, d, x - 1, y), s1.charAt(x - 1), '-', returnarray);

		// Deletion Error
		if (y > 0 && d[x][y] == d[x][y - 1] + 1)
			returnarray = DoubleConcat(doAlignments(s1, s2, d, x, y - 1), '-', s2.charAt(y - 1), returnarray);

		return returnarray;
	}

	/**
	 * Returns pairs of alignment strings for this MSD object's s1/s2 string pair.
	 * 
	 * The alignment strings provide a convenient human-readable way to explain what transformations (insert, delete,
	 * substitute) are employed by the MSD algorithm. It's sort of an explanation of the 'D' matrix.
	 * <p>
	 * 
	 * @return an array of <code>StringPair</code>s containing pairs of alignment strings
	 */
	public StringPair[] getAlignments()
	{
		return doAlignments(s1, s2, d, s1.length(), s2.length());
	}

	/**
	 * Returns the mean size of the alignment string as a double
	 */
	public double meanAlignmentSize()
	{
		StringPair[] sp = getAlignments();
		double n = 0.0;
		for (int i = 0; i < sp.length; ++i)
			n += sp[i].s1.length();
		return n / sp.length;
	}

	/**
	 * Returns the new-and-improved measure for the MSD error rate.
	 * 
	 * The originally proposed MSD error rate was computed by dividing the MSD statistic by the larger of the sizes of
	 * the presented and transcribed text strings. As it turns out, this value differs slightly from the error rate
	 * calculated using our alignment-based error rate measure. This new-and-improved error rate measure fixes this
	 * problem. It is computed by dividing the MSD statistic by the mean size of the alignment strings.
	 */
	public double getErrorRateNew()
	{
		return getMSD() / meanAlignmentSize() * 100.0;
	}
}
