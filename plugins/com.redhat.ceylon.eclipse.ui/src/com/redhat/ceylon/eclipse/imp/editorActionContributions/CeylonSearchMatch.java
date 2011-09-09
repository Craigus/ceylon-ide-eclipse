package com.redhat.ceylon.eclipse.imp.editorActionContributions;

import org.antlr.runtime.Token;
import org.eclipse.core.resources.IFile;
import org.eclipse.search.ui.text.Match;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;

public class CeylonSearchMatch extends Match {
	
	private Tree.Declaration declarationNode;
	
	public CeylonSearchMatch(Tree.Declaration declarationNode, IFile file, int offset, 
			int length, Token location) {
		super(new CeylonElement(declarationNode, file, location), offset, length);
		this.declarationNode=declarationNode;
	}
		
	public Tree.Declaration getDeclarationNode() {
		return declarationNode;
	}
	
}