package com.redhat.ceylon.eclipse.imp.occurrenceMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.imp.language.ILanguageService;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.services.IOccurrenceMarker;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.imp.core.CeylonReferenceResolver;
import com.redhat.ceylon.eclipse.util.FindDeclarationVisitor;
import com.redhat.ceylon.eclipse.util.FindReferenceVisitor;

public class CeylonOccurrenceMarker implements ILanguageService,
		IOccurrenceMarker {
	private List<Object> fOccurrences = Collections.emptyList();

	public String getKindName() {
		return "ceylon Occurence Marker";
	}

	public List<Object> getOccurrencesOf(IParseController parseController,
			Object astNode) {
		if (astNode == null) {
			return Collections.emptyList();
		}

		// Check whether we even have an AST in which to find occurrences
		Tree.CompilationUnit root = (Tree.CompilationUnit) parseController.getCurrentAst();
		if (root == null) {
			return Collections.emptyList();
		}

		fOccurrences = new ArrayList<Object>();
		Declaration declaration = CeylonReferenceResolver.getReferencedDeclaration(astNode);
		if (declaration!=null) {
			FindReferenceVisitor frv = new FindReferenceVisitor(declaration);
			root.visit(frv);
			fOccurrences.addAll(frv.getNodes());
			FindDeclarationVisitor fdv = new FindDeclarationVisitor(declaration);
			root.visit(fdv);
			fOccurrences.add(fdv.getDeclarationNode());
		}
		return fOccurrences;

	}

}