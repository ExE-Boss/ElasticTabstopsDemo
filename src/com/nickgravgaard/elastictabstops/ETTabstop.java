package com.nickgravgaard.elastictabstops;

public class ETTabstop
{
	int trueWidthPix;
	int textWidthPix;
	MutableInteger widestWidthPix = null; // object so we can use refs
	int startPos = 0;
	int endPos = 0;
	boolean endsInTab = false;
	int offset = 0;
	String text;

	public ETTabstop()
	{
	}
}
