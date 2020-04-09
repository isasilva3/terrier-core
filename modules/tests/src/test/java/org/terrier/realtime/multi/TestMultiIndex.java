/*
 * Terrier - Terabyte Retriever 
 * Webpage: http://terrier.org 
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.gla.ac.uk/
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TestMultiIndex.java.
 *
 * The Original Code is Copyright (C) 2004-2020 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Richard McCreadie <richard.mccreadie@glasgow.ac.uk>
 *   Stuart Mackie <s.mackie.1@research.gla.ac.uk>
 */

package org.terrier.realtime.multi;

import static org.junit.Assert.*;
import gnu.trove.TIntHashSet;

import java.io.IOException;
import java.util.Set;


import org.junit.Test;
import org.terrier.indexing.IndexTestUtils;
import org.terrier.realtime.TestUtils;
import org.terrier.structures.CollectionStatistics;
import org.terrier.structures.DocumentIndex;
import org.terrier.structures.Index;
import org.terrier.structures.Lexicon;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.MetaIndex;
import org.terrier.structures.PostingIndex;
import org.terrier.structures.postings.BlockPosting;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.postings.PostingUtil;

import org.terrier.tests.ApplicationSetupBasedTest;
import org.terrier.utility.ApplicationSetup;

import com.google.common.collect.Sets;

public class TestMultiIndex extends ApplicationSetupBasedTest {

	/*
	 * Test MultiIndex.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void test_MultiIndex() throws Exception {
		ApplicationSetup.setProperty("termpipelines", "");
		ApplicationSetup.setProperty("indexer.meta.forward.keys", "filename");
		ApplicationSetup.setProperty("indexer.meta.forward.keylens", "100");
		ApplicationSetup.setProperty("indexer.meta.reverse.keys", "filename");
		Index i1 = IndexTestUtils.makeIndex(new String[]{"0"},new String[]{"one two three"});
		Index i2 = IndexTestUtils.makeIndex(new String[]{"1"},new String[]{"two three four"});
		Index i3 = IndexTestUtils.makeIndex(new String[]{"2"},new String[]{"three four five"});
		MultiIndex mindex = new MultiIndex(new Index[]{i1,i2,i3}, false, false); 
		assertNotNull(mindex);
		Lexicon<String> lexicon = (Lexicon<String>) mindex.getIndexStructure("lexicon");
		assertNotNull(lexicon);
		
		LexiconEntry le = lexicon.getLexiconEntry("three");
		assertNotNull(le);
		assertEquals(3, le.getFrequency());
		assertEquals(1, le.getMaxFrequencyInDocuments());
		assertEquals(3, le.getDocumentFrequency());
		
		
		PostingIndex<?> inverted = mindex.getInvertedIndex();
		assertNotNull(inverted);
		MetaIndex metaindex = (MetaIndex) mindex.getIndexStructure("meta");
		assertNotNull(metaindex);
		DocumentIndex docindex = (DocumentIndex) mindex.getIndexStructure("document");
		assertNotNull(docindex);

		CollectionStatistics stats = mindex.getCollectionStatistics();
		assertNotNull(stats);
		
		PostingIndex<?> direct =  mindex.getDirectIndex();
		assertNotNull(direct);
		
		checkDoc(direct.getPostings(docindex.getDocumentEntry(0)), lexicon, Sets.newHashSet("one", "two", "three"));
		checkDoc(direct.getPostings(docindex.getDocumentEntry(1)), lexicon, Sets.newHashSet("two", "three", "four"));
		checkDoc(direct.getPostings(docindex.getDocumentEntry(2)), lexicon, Sets.newHashSet("three", "four", "five"));
		
	}
	
	private void checkDoc(IterablePosting ip, Lexicon<String> lexicon, Set<String> terms) throws IOException {
		TIntHashSet ids = new TIntHashSet(PostingUtil.getIds(ip));
		for(String t : terms)
		{
			LexiconEntry le = lexicon.getLexiconEntry(t);
			assertTrue(ids.contains(le.getTermId()));
		}
		
		for(int id : ids.toArray())
		{
			String t = lexicon.getLexiconEntry(id).getKey();
			assertTrue(terms.contains(t));
			terms.remove(t);
		}
		assertEquals(0, terms.size());
	}
	
	@Test
	public void test_MultiIndexBlocks() throws Exception {
		ApplicationSetup.setProperty("termpipelines", "");
		ApplicationSetup.setProperty("indexer.meta.forward.keys", "filename");
		ApplicationSetup.setProperty("indexer.meta.forward.keylens", "100");
		ApplicationSetup.setProperty("indexer.meta.reverse.keys", "filename");
		Index i1 = IndexTestUtils.makeIndexBlocks(new String[]{"0"},new String[]{"one two three"});
		Index i2 = IndexTestUtils.makeIndexBlocks(new String[]{"1"},new String[]{"two three four"});
		Index i3 = IndexTestUtils.makeIndexBlocks(new String[]{"2"},new String[]{"three four five"});
		MultiIndex mindex = new MultiIndex(new Index[]{i1,i2,i3}, true, false); 
		assertNotNull(mindex);
		Lexicon<String> lexicon = mindex.getLexicon();
		assertNotNull(lexicon);
		PostingIndex<?> inverted = mindex.getInvertedIndex();
		assertNotNull(inverted);
		
		IterablePosting ip = inverted.getPostings(lexicon.getLexiconEntry("two"));
		assertNotNull(ip);
		assertTrue(ip instanceof BlockPosting);
		assertEquals(0, ip.next());
		assertEquals(1, ip.getFrequency());
		assertEquals(1, ((BlockPosting)ip).getPositions()[0]);
		
		
		MetaIndex metaindex = mindex.getMetaIndex();
		assertNotNull(metaindex);
		DocumentIndex docindex =  mindex.getDocumentIndex();
		assertNotNull(docindex);
		CollectionStatistics stats = mindex.getCollectionStatistics();
		assertNotNull(stats);
	}
	
	/*
	 * Check disk index and multi-index (built from same test data) match up. 
	 */
	@Test public void test_diskVmulti() throws Exception {
		ApplicationSetup.setProperty("termpipelines", "");
		ApplicationSetup.setProperty("indexer.meta.forward.keys", "filename");
		ApplicationSetup.setProperty("indexer.meta.forward.keylens", "100");
		ApplicationSetup.setProperty("indexer.meta.reverse.keys", "filename");
		Index disk = IndexTestUtils.makeIndex(new String[]{"A","B"},new String[]{"one two three","three four five"});
		Index disk1 = IndexTestUtils.makeIndex(new String[]{"A"},new String[]{"one two three"});
		Index disk2 = IndexTestUtils.makeIndex(new String[]{"B"},new String[]{"three four five"});
		Index multi = new MultiIndex(new Index[]{disk1,disk2}, false, false);
		assertNotNull(multi);
		TestUtils.compareIndices(disk,multi);
		//TestUtils.compareProperties(disk,multi);
		TestUtils.compareRetrieval("one",disk,multi);
		TestUtils.compareRetrieval("three",disk,multi);
		TestUtils.compareRetrieval("five",disk,multi);
		TestUtils.checkContents(disk,  "one",   1, new int[]{0},   new int[]{1},   new int[]{3});
		TestUtils.checkContents(multi, "one",   1, new int[]{0},   new int[]{1},   new int[]{3});
		TestUtils.checkContents(disk,  "three", 2, new int[]{0,1}, new int[]{1,1}, new int[]{3,3});
		TestUtils.checkContents(multi, "three", 2, new int[]{0,1}, new int[]{1,1}, new int[]{3,3});
		TestUtils.checkContents(disk,  "five",  1, new int[]{1},   new int[]{1},   new int[]{3});
		TestUtils.checkContents(multi, "five",  1, new int[]{1},   new int[]{1},   new int[]{3});
	}
	


	
}
