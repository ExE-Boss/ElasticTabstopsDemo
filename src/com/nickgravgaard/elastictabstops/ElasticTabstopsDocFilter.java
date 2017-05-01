// ElasticTabstopsDocFilter - a document filter that implements elastic tabstops
// This code is public domain so feel free to use it in any way you see fit
// You don't have to but it would be nice if you sent me any improvements you make
// see http://nickgravgaard.com/elastictabstops/
// last modified by Nick Gravgaard on 2006-07-09

package com.nickgravgaard.elastictabstops;

import java.awt.FontMetrics;
import javax.swing.text.*;

public class ElasticTabstopsDocFilter extends DocumentFilter
{
	FontMetrics m_fm;

	/*
	// tabstops are multiples of 32 pixels plus 8 pixels of padding
	int m_tabMultiples = 32; // must be greater than 0
	int m_tabMinimum = 0;
	int m_tabPadding = 8;
	*/

	// tabstops are at least 32 pixels plus 8 pixels of padding
	int m_tabMultiples = 1; // must be greater than 0
	int m_tabMinimum = 32;
	int m_tabPadding = 8;

	public void setFontMetrics(FontMetrics fm)
	{
		m_fm = fm;
		m_tabPadding = fm.charWidth(' ');
		m_tabMinimum = m_tabPadding * 4;
	}

	private ElasticTabstopsDocFilter()
	{
	}

	public ElasticTabstopsDocFilter(FontMetrics fm)
	{
		setFontMetrics(fm);
	}

	public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException
	{
		super.insertString(fb, offs, str, a);
		StyledDocument doc = (StyledDocument)fb.getDocument();
		stretchTabstops(doc);
	}

	public void remove(FilterBypass fb, int offs, int length) throws BadLocationException
	{
		super.remove(fb, offs, length);
		StyledDocument doc = (StyledDocument)fb.getDocument();
		stretchTabstops(doc);
	}

	public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException
	{
		super.replace(fb, offs, length, str, a);
		StyledDocument doc = (StyledDocument)fb.getDocument();
		stretchTabstops(doc);
	}


// #=====================================#
// #            BEGIN CHANGES            #
// #=====================================#

	// todo: needs optimising - a lot of this should be cached if possible
	void stretchTabstops(StyledDocument doc)
	{
		Element section = doc.getDefaultRootElement();

		int maxTabstops = 64; // todo: magic number hardcoded; Increased to 64
		int lineCount = section.getElementCount();
		ETLine lines[] = new ETLine[lineCount];
		ETTabstop grid[][] = new ETTabstop[lineCount][maxTabstops];

		// initialise array
		for (int l = 0; l < lineCount; l++) // for each line
		{
			lines[l] = new ETLine();
			for (int t = 0; t < maxTabstops; t++) // for each column
			{
				grid[l][t] = new ETTabstop();
			}
		}

		// get width of text in cells
		for (int l = 0; l < lineCount; l++) // for each line
		{
			Element line = section.getElement(l);
			int lineStart = line.getStartOffset();
			int lineEnd = line.getEndOffset();
			lines[l].startPos = lineStart;
			lines[l].endPos = lineEnd;
			try
			{
				String lineText = doc.getText(lineStart, lineEnd - lineStart);
				lines[l].text = lineText;
				int textWidthInTab = 0;
				int currentTabNum = 0;
				int charCount = 0;
				grid[l][currentTabNum].text = lineText;
				int lineLength = lineText.length();
				for (int c = 0; c < lineLength; c++) // for each char in current line
				{
					char currentChar = lineText.charAt(c);
					switch (currentChar) // Use String switch (requires Java 7)
					{
						case '\r': // Fix for Windows
						case '\n':
							grid[l][currentTabNum].trueWidthPix = textWidthInTab;
							grid[l][currentTabNum].textWidthPix = textWidthInTab;
							grid[l][currentTabNum].endsInTab = false;
							grid[l][currentTabNum].endPos = lineStart + c;
							if (charCount != 0)
								grid[l][currentTabNum].text = grid[l][currentTabNum].text.substring(0, charCount);
							textWidthInTab = 0;
							charCount = 0;
							break;
						case '\t':
							grid[l][currentTabNum].endsInTab = true;
							grid[l][currentTabNum].endPos = lineStart + c;
							grid[l][currentTabNum].trueWidthPix = calcTabWidth(textWidthInTab, false);
							grid[l][currentTabNum].textWidthPix = calcTabWidth(textWidthInTab, true);
							grid[l][currentTabNum].text = grid[l][currentTabNum].text.substring(0, charCount + 1);
							currentTabNum++;
							grid[l][currentTabNum].startPos = lineStart + c + 1;
							grid[l][currentTabNum].text = lineText.substring(c);
							lines[l].numTabs++;
							textWidthInTab = 0;
							charCount = 0;
							break;
						default:
							textWidthInTab += m_fm.charWidth(currentChar);
							charCount++;
							break;
					}
				}
			}
			catch (BadLocationException ex)
			{
			}
		}

		// find columns blocks and stretch to fit the widest cell
		for (int t = 0; t < maxTabstops; t++) // for each column
		{
			// all tabstops in column block point to same number
			MutableInteger theWidestWidthPix = new MutableInteger(0); // reference
			int maxWidth = 0;
			int prevNumTabs = 0;
			for (int l = 0; l < lineCount; l++) // for each line
			{
				if (grid[l][t].endsInTab)
				{
					// Apply offset
					int s = lines[l].numTabs - prevNumTabs;
					boolean isPrevLeading = true;
					if (l == 0 || prevNumTabs == 0 || s == 0 || (s < 0 ? grid[l - 1][t - s].widestWidthPix == null : false))
					{
						// Apply default code
						grid[l][t].widestWidthPix = theWidestWidthPix; // copy ref
						if (grid[l][t].textWidthPix < maxWidth)
						{
							grid[l][t].textWidthPix = maxWidth;
						}
						else
						{
							maxWidth = grid[l][t].textWidthPix;
							theWidestWidthPix.val = maxWidth;
						}
					}
					else // Important code begins here
					{
						if (s > 0 ? t < s : t >= -s)
						{
							grid[l][t].widestWidthPix = theWidestWidthPix = new MutableInteger(0); // new reference
							theWidestWidthPix.val = maxWidth = grid[l][t].textWidthPix;
						}
						else if (s > 0)
						{
							grid[l][t].offset = s;
							theWidestWidthPix = grid[l - 1][t - s].widestWidthPix;
							grid[l][t].widestWidthPix = theWidestWidthPix; // copy ref
							int offsetMaxWidth = 0;
							int newMaxWidth = 0;
							for (int ts = 0; ts < s; ts++)
							{
								if (ts >= s - 1)
									offsetMaxWidth += grid[l][ts].textWidthPix;
								newMaxWidth += grid[l][ts].textWidthPix;
							}

							if (grid[l - 1][t - s].textWidthPix < newMaxWidth)
							{
								theWidestWidthPix.val = grid[l - 1][t - s].textWidthPix = newMaxWidth;
							}
							else if (grid[l - 1][t - s].textWidthPix < grid[l][t].textWidthPix)
							{
								theWidestWidthPix.val = (grid[l - 1][t - s].textWidthPix = offsetMaxWidth + grid[l][t].textWidthPix);
							}
							else if (newMaxWidth < grid[l - 1][t - s].textWidthPix)
							{
								theWidestWidthPix.val = grid[l][t].textWidthPix = grid[l - 1][t - s].textWidthPix;
							}
							else
							{
								maxWidth = grid[l][t].textWidthPix;
								theWidestWidthPix.val = maxWidth;
							}
						}
						else if (s < 0)
						{
							grid[l][t].offset = s;
							theWidestWidthPix = grid[l - 1][t - s].widestWidthPix;
							grid[l][t].widestWidthPix = theWidestWidthPix; // copy ref
							int newMaxWidth = 0;
							for (int ts = 0; ts < -s; ts++)
							{
								newMaxWidth += grid[l][ts].textWidthPix;
							}

							if (grid[l - 1][t - s].textWidthPix < newMaxWidth)
							{
								theWidestWidthPix.val = grid[l - 1][t - s].textWidthPix = newMaxWidth;
							}
							else if (grid[l - 1][t - s].textWidthPix < grid[l][t].textWidthPix)
							{
								theWidestWidthPix.val = (grid[l - 1][t - s].textWidthPix = newMaxWidth + grid[l][t].textWidthPix);
							}
							else if (newMaxWidth < grid[l - 1][t - s].textWidthPix)
							{
								theWidestWidthPix.val = grid[l][t].textWidthPix = grid[l - 1][t - s].textWidthPix;
							}
							else
							{
								maxWidth = grid[l][t].textWidthPix;
								theWidestWidthPix.val = maxWidth;
							}
						}
					}
				}
				else // end column block
				{
					theWidestWidthPix = new MutableInteger(0); // reference
					maxWidth = 0;
				}

				prevNumTabs = lines[l].numTabs;
			}
		}

		// apply tabstop sizes to the text
		for (int l = 0; l < lineCount; l++) // for each line
		{
			// accumulate tabstop widths
			int accTabstop = 0;
			for (int t = 0; t < lines[l].numTabs; t++)
			{
				int s = grid[l][t].offset;
				if (s == 0 || "\t".equals(grid[l - 1][t - s].text))
				{
					accTabstop += grid[l][t].widestWidthPix.val;
					grid[l][t].trueWidthPix = grid[l][t].textWidthPix;
					grid[l][t].textWidthPix = accTabstop;
				}
				else
				{
					accTabstop += (grid[l][t].widestWidthPix.val - grid[l][t - 1].trueWidthPix);
					grid[l][t].trueWidthPix = grid[l][t].textWidthPix;
					grid[l][t].textWidthPix = accTabstop;
				}
			}

			Element line = section.getElement(l);
			int lineStart = line.getStartOffset();
			int lineEnd = line.getEndOffset();
			setBlocksTabstops(doc, lineStart, lineEnd, grid[l], lines[l].numTabs);
		}
	}

	int calcTabWidth(int textWidthInTab, boolean includePadding)
	{
		if (includePadding)
			textWidthInTab = ((textWidthInTab / m_tabMultiples) + 1) * m_tabMultiples;
		if (textWidthInTab < m_tabMinimum)
		{
			textWidthInTab = m_tabMinimum;
		}
		if (includePadding)
			textWidthInTab += m_tabPadding;
		return textWidthInTab;
	}

// #=====================================#
// #             END CHANGES             #
// #=====================================#

	void setBlocksTabstops(StyledDocument doc, int start, int length, ETTabstop[] tabstopPositions, int tabstopCount)
	{
		TabStop[] tabs = new TabStop[tabstopCount];

		for (int j = 0; j < tabstopCount; j++)
		{
			tabs[j] = new TabStop(tabstopPositions[j].textWidthPix);
		}

		TabSet tabSet = new TabSet(tabs);
		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setTabSet(attributes, tabSet);

		doc.setParagraphAttributes(start, length, attributes, false);
	}

}
