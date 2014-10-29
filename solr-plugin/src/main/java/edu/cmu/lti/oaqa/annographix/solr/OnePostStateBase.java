/*
 *  Copyright 2014 Carnegie Mellon University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 *  The author re-used some of the code from org.apache.lucene.search.PhraseQuery,
 *  which was also licensed under the Apache License 2.0.
 *  
 */
package edu.cmu.lti.oaqa.annographix.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.lucene.index.DocsAndPositionsEnum;

import edu.cmu.lti.oaqa.annographix.solr.StructQueryParse.ConstraintType;
import edu.cmu.lti.oaqa.annographix.solr.StructQueryParse.FieldType;

/** 
 * This a base helper class to read annotation and token info from posting lists. 
 * 
 * @author Leonid Boytsov
 *
 */
public abstract class OnePostStateBase {
  public static int NO_MORE_DOCS = org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
  // TODO change to a larger value
  protected static int INIT_SIZE = 1;
  
  /**
   * 
   * Creates a wrapper of the right type.
   * 
   * @param posting     a posting to wrap.
   * @param type        a posting type: annotation or regular token.
   * @param connectQty  a number of postings connected with a given node/posting 
   *                    via a query graph.
   * 
   * @return            a reference to the newly created object
   */
  public static OnePostStateBase createPost(DocsAndPositionsEnum posting, 
                                            FieldType type,
                                            int connectQty) {
    return type == FieldType.FIELD_ANNOTATION ? 
                      new OnePostStateAnnot(posting, connectQty):
                      new OnePostStateToken(posting, connectQty);
  }
  
  public OnePostStateBase(DocsAndPositionsEnum posting, int connectQty) {
    mPosting = posting;
    mConnectQty = connectQty;
    extendElemInfo(INIT_SIZE);
  }
  
  /**
   * @return a current document id.
   */
  public int getDocID() { return mDocId; }
  
  /**
   * @return an i-th element
   */
  public ElemInfoData getElement(int i) { return mSortedElemInfo[i]; }
  
  /**
   * @return a current element
   */
  public ElemInfoData getCurrElement() { return mSortedElemInfo[mCurrElemIndx]; }
  
  /**
   * Changes the internal index pointing to the current element, 
   * without checking if the index is invalid.
   * 
   * @param index   an index to set.
   */
  public void setCurrElemIndex(int index) {
    mCurrElemIndx = index;
  }
  
  /**
   * @return an internal element index
   */
  public int getCurrElemIndex() { return mCurrElemIndx; }
  
  /**
   * Find an element with an offset larger than the specified one.
   * <p>
   * This function assumes that elements are sorted by in the order of 
   * non-decreasing offsets. It is not thread-safe, because it is
   * reusing the search key (we don't want to allocate memory every
   * time we call this function). 
   * </p> 
   *
   * @param     minOffset   find elements with offset > minOffset.
   * @param     minIndx     search among elements with the index >= minIndx
   *  
   * @return    the index (>= minIndx) of the first element 
   *            whose offset > minOffset or {@link #getQty()} if such
   *            element cannot be found. 
   *             
   *  
   */
  public int findElemIndexWithLargerOffset(int minOffset, int minIndx) {
    mSearchKey.mStartOffset = minOffset;
    int res = Arrays.binarySearch(mSortedElemInfo, 
                              Math.max(0, minIndx), mQty,
                              mSearchKey, mOffsetComp);
    if (res >= 0) {
      // Find the first element larger than res
      while (res < mQty && 
             mSortedElemInfo[res].mStartOffset == minOffset)  {
        ++res;
      }
    } else {
      // found the first element with a larger offset
      res =- res;
    }
    return res;
  }
  
  /**
   * @return a number of elements in a current document.
   */
  public int getQty() { return mQty; }
  
  /**
   * @return a cost associated with the posting.
   */
  public long getPostCost() { return mPosting.cost(); }
  
  /**
   * @return a number of postings connected with a given node/posting 
   *         via a query graph.
   */
  public int getConnectQty() { return mConnectQty; }
  
  /**
   * Move to the first document with id >= docId.
   * 
   * @param docId           find a document with id at least this large.
   * @return                the first document with id >= docId or {@link OnePostStateBase#NO_MORE_DOCS} if no docs are available with ids >= docId.
   * @throws IOException
   */
  public int advance(int docId) throws IOException {
    mQty = 0;
    if (mDocId != NO_MORE_DOCS)
      mDocId = mPosting.advance(docId);
    if (mDocId != NO_MORE_DOCS) {
      readDocElements();
    }
    return mDocId;
  }
  
  /**
   * Move to the next available document.
   * 
   * @return    the next document iD or {@link OnePostStateBase#NO_MORE_DOCS} if no docs are available.
   * @throws IOException
   */
  public int nextDoc() throws IOException {
    mQty = 0;
    if (mDocId != NO_MORE_DOCS)
      mDocId = mPosting.nextDoc();
    if (mDocId != NO_MORE_DOCS) {
      readDocElements();
    }
    return mDocId;
  }  
  
  /**
   * Memorize constraints associated with the given posting.
   * 
   * <p>
   * The list of constraints includes a list of constraint types,
   * as well as the list of constraint nodes. Unfortunately, it is 
   * not possible to initialize such a list in a constructor, because
   * some of the elements may not exist at the time when constructor is
   * called.
   * </p>
   * 
   * @param     constrType      a list of constraint types.
   * @param     constrNode      a list of constraint/dependent nodes.
   * 
   * @throws Exception
   */
  public void setConstraints(ArrayList<StructQueryParse.ConstraintType> constrType,
                             ArrayList<OnePostStateBase> constrNode) {
    if (constrNode.size() != constrType.size()) {
      throw new RuntimeException("Bug: constrType.size() != constrNode.size()");
    }
    mConstrType = new StructQueryParse.ConstraintType[constrType.size()];
    mConstrNode = new OnePostStateBase[constrNode.size()];
    
    for (int i = 0; i < constrType.size(); ++i) {
      mConstrType[i] = constrType.get(i);
      mConstrNode[i] = constrNode.get(i);
    }    
  }
  /**
   * Memorize an index in the sorted array of postings
   * 
   * @param     sortIndx        an index in the array of postings sorted
   *                            using SortPostByConnectQtyAndCost.
   */
  public void setSortIndex(int sortIndx) {
    mSortIndx = sortIndx;
  }
  
  /**
   * @return    an index in the array of postings sorted
   *            using SortPostByConnectQtyAndCost.
   */
  public int getSortIndex() { return mSortIndx; }
  
  /**
   * Check if constraints involving the current node and the node
   * with the index sortIndx are satisfied. 
   * <p>The complexity of this operation is O(Nconstr). In real
   * scenarios, we expect Nconstr to be small (around 2-5). 
   * In this case, iterating over an array is faster than 
   * retrieving data from a hash.  
   * 
   * @param     sortIndx
   * 
   * @return    true if constraints are satisfied and false otherwise.
   */
  public boolean checkConstraints(int sortIndx) {
    for (int k = 0; k < mConstrType.length; ++k) {
      OnePostStateBase  nodeOther = mConstrNode[k];
      
      if (nodeOther.getSortIndex() == sortIndx) { 
        ElemInfoData    eCurr = mSortedElemInfo[mCurrElemIndx];
        ElemInfoData    eOther = nodeOther.getCurrElement();
        
        if (mConstrType[k] == ConstraintType.CONSTRAINT_PARENT){
          if (eCurr.mId != eOther.mParentId) return false;
        } else {
          // mConstrType[k] == ConstraintType.CONSTRAINT_CONTAINS
          if (eOther.mStartOffset < eCurr.mStartOffset ||
              eOther.mEndOffset > eCurr.mEndOffset) return false;
        }
      }
    }
    return true;
  }
  
  /**
   * This function read one-document tokens/annotations start and end offsets.
   * All read entries are supposed to be sorted by the start offset!
   */
  protected abstract void readDocElements()  throws IOException;
  
  protected void readDocElementsBase() throws IOException {
    mCurrElemIndx=0;
    mQty = mPosting.freq();
    // Ensure we have enough space to store 
    extendElemInfo(mQty);    
  }
  
  /**
   * Re-allocate the mElemInfo array if necessary.
   * 
   * @param newCapacity  a new minimum number of elements to accommodate.
   */
  protected void extendElemInfo(int newCapacity) {
    if (newCapacity > mSortedElemInfo.length) {
      ElemInfoData oldElemInfo[] = mSortedElemInfo;
      mSortedElemInfo = new ElemInfoData[newCapacity * 2];
      for (int i = 0; i < oldElemInfo.length; ++i) {
        mSortedElemInfo[i] = oldElemInfo[i]; // re-use previously allocated memory
      }
      for (int i = oldElemInfo.length; i < mSortedElemInfo.length; ++i) {
        mSortedElemInfo[i] = new ElemInfoData();
      }
    }
  }
  
  protected DocsAndPositionsEnum    mPosting;
  protected int                     mDocId=1;
  protected int                     mCurrElemIndx=-1;
  protected int                     mQty = 0;
  /** Elements are supposed to be started by offset. */
  protected ElemInfoData            mSortedElemInfo[] = new ElemInfoData[0];
  protected ElemInfoData            mSearchKey = new ElemInfoData();
  protected StartOffsetElemInfoDataComparator   mOffsetComp = 
                                      new StartOffsetElemInfoDataComparator();
  
  protected StructQueryParse.ConstraintType[]     mConstrType = null;
  protected OnePostStateBase[]                    mConstrNode = null;
  protected int                                   mConnectQty = 0;
  protected int                                   mSortIndx = -1;

}

class StartOffsetElemInfoDataComparator implements Comparator<ElemInfoData> {

  @Override
  public int compare(ElemInfoData o1, ElemInfoData o2) {
    return o1.mStartOffset - o2.mStartOffset;
  }
}

