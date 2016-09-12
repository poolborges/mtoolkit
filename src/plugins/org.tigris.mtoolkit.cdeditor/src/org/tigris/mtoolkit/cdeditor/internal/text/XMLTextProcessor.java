/*******************************************************************************
 * Copyright (c) 2003, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.cdeditor.internal.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.internal.core.text.IDocumentAttributeNode;
import org.eclipse.pde.internal.core.text.IDocumentElementNode;
import org.eclipse.pde.internal.core.text.IDocumentTextNode;
import org.eclipse.pde.internal.core.util.PDEXMLHelper;
import org.eclipse.pde.internal.core.util.PropertiesUtil;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MoveSourceEdit;
import org.eclipse.text.edits.MoveTargetEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.tigris.mtoolkit.cdeditor.internal.CDEditorPlugin;



public class XMLTextProcessor {
	protected HashMap fOperationTable = new HashMap();
	protected HashMap fMoveOperations = new HashMap();
	protected ArrayList fEditOperations = new ArrayList();
	
	private IDocument document;

	public XMLTextProcessor(IDocument document) {
		this.document = document;
	}

	public void handleDocumentEvent(DocumentChangedEvent event) {
		addTextEditOperation(fEditOperations, event);
	}
	
	public void flush() {
		flushModel(document);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.neweditor.context.InputContext#addTextEditOperation(java.util.ArrayList, org.eclipse.pde.core.IModelChangedEvent)
	 */
	protected void addTextEditOperation(ArrayList ops, DocumentChangedEvent event) {
		Object[] objects = event.getChangedObjects();
		if (objects != null) {
			for (int i = 0; i < objects.length; i++) {
				Object object = objects[i];
				switch (event.getChangeType()) {
					case IModelChangedEvent.REMOVE :
						if (object instanceof IDocumentElementNode)
							removeNode((IDocumentElementNode) object, ops);
						break;
					case IModelChangedEvent.INSERT :
						if (object instanceof IDocumentElementNode)
							insertNode((IDocumentElementNode) object, ops);
						break;
					case IModelChangedEvent.CHANGE :
						if (object instanceof IDocumentElementNode) {
							IDocumentElementNode node = (IDocumentElementNode) object;
							IDocumentAttributeNode attr = node.getDocumentAttribute(event.getChangedProperty());
							if (attr != null) {
								addAttributeOperation(attr, ops, event);
							} else if (event.getOldValue() instanceof IDocumentElementNode && event.getNewValue() instanceof IDocumentElementNode) {
								// swapping of nodes
								modifyNode(node, ops, event);
							}
						} else if (object instanceof IDocumentTextNode) {
							addElementContentOperation((IDocumentTextNode) object, ops);
						}
					default :
						break;
				}
			}
		}
	}

	private void removeNode(IDocumentElementNode node, ArrayList ops) {
		// delete previous op on this node, if any
		TextEdit old = (TextEdit) fOperationTable.get(node);
		if (old != null) {
			ops.remove(old);
			fOperationTable.remove(node);
		}
		TextEdit oldMove = (TextEdit) fMoveOperations.get(node);
		if (oldMove != null) {
			ops.remove(oldMove);
			fMoveOperations.remove(node);
		}
		// if node has an offset, delete it
		if (node.getOffset() > -1) {
			// Create a delete op for this node
			TextEdit op = getDeleteNodeOperation(node);
			ops.add(op);
			fOperationTable.put(node, op);
		} else if (old == null && oldMove == null) {
			// No previous op on this non-offset node, just rewrite highest ancestor with an offset
			insertNode(node, ops);
		}
	}

	private void insertNode(IDocumentElementNode node, ArrayList ops) {
		TextEdit op = null;
		node = getHighestNodeToBeWritten(node);
		if (node.getParentNode() == null) {
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=163161
			if (node.isRoot())
				op = new InsertEdit(0, node.write(true));
		} else {
			if (node.getOffset() > -1) {
				// this is an element that was of the form <element/>
				// it now needs to be broken up into <element><new/></element>
				op = new ReplaceEdit(node.getOffset(), node.getLength(), node.write(false));
			} else {
				// try to insert after last sibling that has an offset
				op = insertAfterSibling(node);
				// insert as first child of its parent
				if (op == null) {
					op = insertAsFirstChild(node);
				}
			}
		}
		TextEdit old = (TextEdit) fOperationTable.get(node);
		if (old != null)
			ops.remove(old);
		if (op != null) {
			ops.add(op);
			fOperationTable.put(node, op);
		}
	}

	private InsertEdit insertAfterSibling(IDocumentElementNode node) {
		IDocumentElementNode sibling = node.getPreviousSibling();
		for (;;) {
			if (sibling == null)
				break;
			if (sibling.getOffset() > -1) {
				node.setLineIndent(sibling.getLineIndent());
				String sep = TextUtilities.getDefaultLineDelimiter(document);
				return new InsertEdit(sibling.getOffset() + sibling.getLength(), sep + node.write(true));
			}
			sibling = sibling.getPreviousSibling();
		}
		return null;
	}

	private InsertEdit insertAsFirstChild(IDocumentElementNode node) {
		int offset = node.getParentNode().getOffset();
		int length = getNextPosition(document, offset, '>');
		node.setLineIndent(node.getParentNode().getLineIndent() + 3);
		String sep = TextUtilities.getDefaultLineDelimiter(document);
		return new InsertEdit(offset + length + 1, sep + node.write(true));
	}

	private void modifyNode(IDocumentElementNode node, ArrayList ops, DocumentChangedEvent event) {
		IDocumentElementNode oldNode = (IDocumentElementNode) event.getOldValue();
		IDocumentElementNode newNode = (IDocumentElementNode) event.getNewValue();

		IDocumentElementNode node1 = (oldNode.getPreviousSibling() == null || oldNode.equals(newNode.getPreviousSibling())) ? oldNode : newNode;
		IDocumentElementNode node2 = node1.equals(oldNode) ? newNode : oldNode;

		if (node1.getOffset() < 0 && node2.getOffset() < 2) {
			TextEdit op = (TextEdit) fOperationTable.get(node1);
			if (op == null) {
				// node 1 has no rule, so node 2 has no rule, therefore rewrite parent/ancestor
				insertNode(node, ops);
			} else {
				// swap order of insert operations
				TextEdit op2 = (TextEdit) fOperationTable.get(node2);
				ops.set(ops.indexOf(op), op2);
				ops.set(ops.indexOf(op2), op);
			}
		} else if (node1.getOffset() > -1 && node2.getOffset() > -1) {
			// both nodes have offsets, so create a move target/source combo operation
			IRegion region = getMoveRegion(node1);
			MoveSourceEdit source = new MoveSourceEdit(region.getOffset(), region.getLength());
			region = getMoveRegion(node2);
			source.setTargetEdit(new MoveTargetEdit(region.getOffset()));
			MoveSourceEdit op = (MoveSourceEdit) fMoveOperations.get(node1);
			if (op != null) {
				ops.set(ops.indexOf(op), source);
			} else {
				op = (MoveSourceEdit) fMoveOperations.get(node2);
				if (op != null && op.getTargetEdit().getOffset() == source.getOffset()) {
					fMoveOperations.remove(node2);
					ops.remove(op);
					return;
				}
				ops.add(source);
			}
			fMoveOperations.put(node1, source);
		} else {
			// one node with offset, the other without offset.  Delete/reinsert the one without offset
			insertNode((node1.getOffset() < 0) ? node1 : node2, ops);
		}
	}

	private IRegion getMoveRegion(IDocumentElementNode node) {
		int offset = node.getOffset();
		int length = node.getLength();
		int i = 1;
		try {
			IDocument doc = document;
			for (;; i++) {
				char ch = doc.get(offset - i, 1).toCharArray()[0];
				if (!Character.isWhitespace(ch)) {
					i -= 1;
					break;
				}
			}
		} catch (BadLocationException e) {
		}
		return new Region(offset - i, length + i);
	}

	private void addAttributeOperation(IDocumentAttributeNode attr, ArrayList ops, DocumentChangedEvent event) {
		int offset = attr.getValueOffset();
		Object newValue = event.getNewValue();
		Object changedObject = attr;
		TextEdit op = null;
		if (offset > -1) {
			if (newValue == null || newValue.toString().length() == 0) {
				int length = attr.getValueOffset() + attr.getValueLength() + 1 - attr.getNameOffset();
				op = getAttributeDeleteEditOperation(attr.getNameOffset(), length);
			} else {
				op = new ReplaceEdit(offset, attr.getValueLength(), getWritableAttributeNodeValue(event.getNewValue().toString()));
			}
		}

		if (op == null) {
			IDocumentElementNode node = attr.getEnclosingElement();
			IDocument doc = document;
			if (node.getOffset() > -1) {
				changedObject = node;
//				int len;
//				if (node.canTerminateStartTag())
//					len = node.getLength() - 1;
//				else
//					len = getNextPosition(doc, node.getOffset(), '>');
//				op = new ReplaceEdit(node.getOffset(), len + 1, node.writeShallow(node.canTerminateStartTag()));
				int len = getNextPosition(doc, node.getOffset(), '>');
				op = new ReplaceEdit(node.getOffset(), len + 1, node.writeShallow(shouldTerminateElement(doc, node.getOffset() + len)));
			} else {
				insertNode(node, ops);
				return;
			}
		}
		TextEdit oldOp = (TextEdit) fOperationTable.get(changedObject);
		if (oldOp != null)
			ops.remove(oldOp);
		ops.add(op);
		fOperationTable.put(changedObject, op);
	}

	private void addElementContentOperation(IDocumentTextNode textNode, ArrayList ops) {
		TextEdit op = null;
		Object changedObject = textNode;
		if (textNode.getOffset() > -1) {
			String newText = getWritableTextNodeString(textNode);
			// XXX: add additional processing to properly indent all of the lines
			op = new ReplaceEdit(textNode.getOffset(), textNode.getLength(), newText);
		} else {
			IDocumentElementNode parent = textNode.getEnclosingElement();
			if (parent.getOffset() > -1) {
				IDocument doc = document;
				try {
					String endChars = doc.get(parent.getOffset() + parent.getLength() - 2, 2);
					if ("/>".equals(endChars)) { //$NON-NLS-1$
						// parent element is of the form <element/>, rewrite it
						insertNode(parent, ops);
						return;
					}
				} catch (BadLocationException e) {
				}
				// add text as first child
				changedObject = parent;
				String sep = TextUtilities.getDefaultLineDelimiter(document);
				StringBuffer buffer = new StringBuffer(sep);
				for (int i = 0; i < parent.getLineIndent(); i++)
					buffer.append(" "); //$NON-NLS-1$
				buffer.append("   " + getWritableTextNodeString(textNode)); //$NON-NLS-1$
				int offset = parent.getOffset();
				int length = getNextPosition(doc, offset, '>');
				op = new InsertEdit(offset + length + 1, buffer.toString());
			} else {
				insertNode(parent, ops);
				return;
			}
		}
		TextEdit oldOp = (TextEdit) fOperationTable.get(changedObject);
		if (oldOp != null)
			ops.remove(oldOp);
		ops.add(op);
		fOperationTable.put(changedObject, op);
	}

	private boolean shouldTerminateElement(IDocument doc, int offset) {
		try {
			return doc.get(offset - 1, 1).toCharArray()[0] == '/';
		} catch (BadLocationException e) {
		}
		return false;
	}

	private int getNextPosition(IDocument doc, int offset, char ch) {
		int i = 0;
		try {
			for (i = 0; i + offset < doc.getLength(); i++) {
				if (ch == doc.getChar(offset + i))
					break;
			}
		} catch (BadLocationException e) {
		}
		return i;
	}

	private DeleteEdit getAttributeDeleteEditOperation(int offset, int length) {
		try {
			IDocument doc = document;
			// Traverse backwards in the document starting at the attribute
			// offset
			// Goal: Delete all whitespace preceding the attribute name 
			// including spaces, tabs and newlines 
			// We want the next attribute (if defined) to be in the same 
			// position as the deleted one and properly indented.  Otherwise,
			// we want the open angle bracket to be adjacent to the start 
			// element tag name or adjacent to the previous attribute (if
			// defined) before the deleted one
			// e.g.   _____\n________att1="value1"
			// This is accomplished by growing the length and decrementing
			// the offset in order to include the extra whitespace in the
			// deletion operation
			for (int i = (offset - 1); i >= 0; i--) {
				// Get the character at the specified document index
				char character = doc.getChar(i);
				// If the character is whitespace, include it in the deletion
				// operation
				if (Character.isWhitespace(character)) {
					// Grow length by one
					length = length + 1;
					// Decrement offset by one
					offset = offset - 1;
				} else {
					// Non-whitespace character encountered, do not mark it 
					// for deletion and we are done
					break;
				}
			}
		} catch (BadLocationException e) {
		}
		return new DeleteEdit(offset, length);
	}

	private DeleteEdit getDeleteNodeOperation(IDocumentElementNode node) {
		int offset = node.getOffset();
		int length = node.getLength();
		try {
			IDocument doc = document;
			// node starts on this line:
			int startLine = doc.getLineOfOffset(offset);
			// 1st char on startLine has this offset:
			int startLineOffset = doc.getLineOffset(startLine);
			// hunt down 1st whitespace/start of line with startOffset:
			int startOffset;
			// loop backwards to the beginning of the line, stop if we find non-whitespace
			for (startOffset = offset - 1; startOffset >= startLineOffset; startOffset -= 1)
				if (!Character.isWhitespace(doc.getChar(startOffset)))
					break;

			// move forward one (loop stopped after reaching too far)
			startOffset += 1;

			// node ends on this line:
			int endLine = doc.getLineOfOffset(offset + length);
			// length of last line's delimiter:
			int endLineDelimLength = doc.getLineDelimiter(endLine).length();
			// hunt last whitespace/end of line with extraLength:
			int extraLength = length;
			while (true) {
				extraLength += 1;
				if (!Character.isWhitespace(doc.getChar(offset + extraLength))) {
					// found non-white space, move back one
					extraLength -= 1;
					break;
				}
				if (doc.getLineOfOffset(offset + extraLength) > endLine) {
					// don't want to touch the lineDelimeters
					extraLength -= endLineDelimLength;
					break;
				}
			}

			// if we reached start of line, remove newline
			if (startOffset == startLineOffset && startLine > 0)
				startOffset -= doc.getLineDelimiter(startLine - 1).length();

			// add difference of new offset
			length = extraLength + (offset - startOffset);
			offset = startOffset;
//			printDeletionRange(offset, length);
		} catch (BadLocationException e) {
		}
		return new DeleteEdit(offset, length);
	}

	protected void printDeletionRange(int offset, int length) {
		try {
			// newlines printed as \n
			// carriage returns printed as \r
			// tabs printed as \t
			// spaces printed as *
			String string = document.get(offset, length);
			StringBuffer buffer = new StringBuffer();
			for (int i = 0; i < string.length(); i++) {
				char c = string.charAt(i);
				if (c == '\n')
					buffer.append("\\n"); //$NON-NLS-1$
				else if (c == '\r')
					buffer.append("\\r"); //$NON-NLS-1$
				else if (c == '\t')
					buffer.append("\\t"); //$NON-NLS-1$
				else if (c == ' ')
					buffer.append('*');
				else
					buffer.append(c);
			}
			System.out.println(buffer.toString());
		} catch (BadLocationException e) {
		}
	}

	private IDocumentElementNode getHighestNodeToBeWritten(IDocumentElementNode node) {
		IDocumentElementNode parent = node.getParentNode();
		if (parent == null)
			return node;
		if (parent.getOffset() > -1) {
			IDocument doc = document;
			try {
				String endChars = doc.get(parent.getOffset() + parent.getLength() - 2, 2);
				return ("/>".equals(endChars)) ? parent : node; //$NON-NLS-1$
			} catch (BadLocationException e) {
				return node;
			}

		}
		return getHighestNodeToBeWritten(parent);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.neweditor.context.InputContext#flushModel(org.eclipse.jface.text.IDocument)
	 */
	protected void flushModel(IDocument doc) {
		removeUnnecessaryOperations();
		if (fOperationTable.size() == 1) {
			Object object = fOperationTable.keySet().iterator().next();
			if (object instanceof IDocumentElementNode && fEditOperations.get(0) instanceof InsertEdit) {
				if (((IDocumentElementNode) object).getParentNode() == null) {
					doc.set(((IDocumentElementNode) object).write(true));
					fOperationTable.clear();
					fEditOperations.clear();
					return;
				}
			}
		}
		reorderInsertEdits(fEditOperations);
		fOperationTable.clear();
		fMoveOperations.clear();
		superFlushModel(doc);
	}
	
	
	/*
	 * Methods copied from superclasses of XMLInputContext
	 */
	protected boolean isNewlineNeeded(IDocument doc) throws BadLocationException {
		return PropertiesUtil.isNewlineNeeded(doc);
	}
	
	protected static void insert(TextEdit parent, TextEdit edit) {
		if (!parent.hasChildren()) {
			parent.addChild(edit);
			if (edit instanceof MoveSourceEdit) {
				parent.addChild(((MoveSourceEdit) edit).getTargetEdit());
			}
			return;
		}
		TextEdit[] children = parent.getChildren();
		// First dive down to find the right parent.
		for (int i = 0; i < children.length; i++) {
			TextEdit child = children[i];
			if (covers(child, edit)) {
				insert(child, edit);
				return;
			}
		}
		// We have the right parent. Now check if some of the children have to
		// be moved under the new edit since it is covering it.
		for (int i = children.length - 1; i >= 0; i--) {
			TextEdit child = children[i];
			if (covers(edit, child)) {
				parent.removeChild(i);
				edit.addChild(child);
			}
		}
		parent.addChild(edit);
		if (edit instanceof MoveSourceEdit) {
			parent.addChild(((MoveSourceEdit) edit).getTargetEdit());
		}
	}

	protected static boolean covers(TextEdit thisEdit, TextEdit otherEdit) {
		if (thisEdit.getLength() == 0) // an insertion point can't cover anything
			return false;

		int thisOffset = thisEdit.getOffset();
		int thisEnd = thisEdit.getExclusiveEnd();
		if (otherEdit.getLength() == 0) {
			int otherOffset = otherEdit.getOffset();
			return thisOffset < otherOffset && otherOffset < thisEnd;
		}
		int otherOffset = otherEdit.getOffset();
		int otherEnd = otherEdit.getExclusiveEnd();
		return thisOffset <= otherOffset && otherEnd <= thisEnd;
	}

	protected void superFlushModel(IDocument doc) {
		if (fEditOperations.size() > 0) {
			try {
				MultiTextEdit edit = new MultiTextEdit();
				if (isNewlineNeeded(doc))
					insert(edit, new InsertEdit(doc.getLength(), TextUtilities.getDefaultLineDelimiter(doc)));
				for (int i = 0; i < fEditOperations.size(); i++) {
					insert(edit, (TextEdit) fEditOperations.get(i));
				}
				edit.apply(doc);
				fEditOperations.clear();
			} catch (MalformedTreeException e) {
				CDEditorPlugin.log(e);
			} catch (BadLocationException e) {
				CDEditorPlugin.log(e);
			}
		}
		// If no errors were encountered flushing the model, then undirty the
		// model.  This needs to be done regardless of whether there are any
		// edit operations or not; since, the contributed actions need to be
		// updated and the editor needs to be undirtied
	}

	// Not sure what this does, need investigation
	protected void reorderInsertEdits(ArrayList ops) {
		
	}

	protected void removeUnnecessaryOperations() {
		Iterator iter = fOperationTable.values().iterator();
		while (iter.hasNext()) {
			Object object = iter.next();
			if (object instanceof IDocumentElementNode) {
				IDocumentElementNode node = (IDocumentElementNode) object;
				if (node.getOffset() > -1) {
					IDocumentAttributeNode[] attrs = node.getNodeAttributes();
					for (int i = 0; i < attrs.length; i++) {
						Object op = fOperationTable.remove(attrs[i]);
						if (op != null)
							fEditOperations.remove(op);
					}
					IDocumentTextNode textNode = node.getTextNode();
					if (textNode != null) {
						Object op = fOperationTable.remove(textNode);
						if (op != null)
							fEditOperations.remove(op);
					}
				}
			}
		}
	}

	/**
	 * @param source
	 * @return
	 */
	protected String getWritableAttributeNodeValue(String source) {
		// TODO: MP: TEO: LOW: Shouldn't it be getWritableAttributeString ?
		return PDEXMLHelper.getWritableString(source);
	}

	/**
	 * @param source
	 * @return
	 */
	protected String getWritableTextNodeString(IDocumentTextNode textNode) {
		return textNode.write();
	}

	protected HashMap getOperationTable() {
		return fOperationTable;
	}

}