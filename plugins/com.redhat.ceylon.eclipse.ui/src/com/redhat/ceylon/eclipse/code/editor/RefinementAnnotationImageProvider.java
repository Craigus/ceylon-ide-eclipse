package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_DEFAULT_REFINEMENT;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CEYLON_FORMAL_REFINEMENT;
import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.imageRegistry;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.texteditor.IAnnotationImageProvider;

public class RefinementAnnotationImageProvider implements IAnnotationImageProvider {
    
    private static Image DEFAULT = imageRegistry.get(CEYLON_DEFAULT_REFINEMENT);
    private static Image FORMAL = imageRegistry.get(CEYLON_FORMAL_REFINEMENT);
    
    @Override
    public Image getManagedImage(Annotation annotation) {
        return ((RefinementAnnotation) annotation).getDeclaration().isFormal() ? 
                FORMAL : DEFAULT;
    }
    
    @Override
    public String getImageDescriptorId(Annotation annotation) {
        return null;
    }
    
    @Override
    public ImageDescriptor getImageDescriptor(String imageDescritporId) {
        return null;
    }
    
}
