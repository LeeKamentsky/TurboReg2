/**
 * 
 */
package fiji.plugin.turboreg2;

/**
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 * 
 * The transformation models supported by TurboReg
 *
 */
public enum TransformationType {
	/*********************************************************************
	 A translation is described by a single point. It keeps area, angle,
	 and orientation. A translation is determined by 2 parameters.
	 ********************************************************************/
	TRANSLATION("Translation"),
	/*********************************************************************
	 A single points determines the translation component of a rigid-body
	 transformation. As the rotation is given by a scalar number, it is
	 not natural to represent this number by coordinates of a point. The
	 rigid-body transformation is determined by 3 parameters.
	 ********************************************************************/
	RIGID_BODY("Rigid body"),
	/*********************************************************************
	 A pair of points determines the combination of a translation, of
	 a rotation, and of an isotropic scaling. Angles are conserved. A
	 scaled rotation is determined by 4 parameters.
	 ********************************************************************/
	SCALED_ROTATION("Scaled rotation"),
	/*********************************************************************
	 Three points generate an affine transformation, which is any
	 combination of translation, rotation, isotropic scaling, anisotropic
	 scaling, shearing, and skewing. An affine transformation maps
	 parallel lines onto parallel lines and is determined by 6 parameters.
	 ********************************************************************/
	AFFINE("Affine"),
	/*********************************************************************
	 Four points describe a bilinear transformation, where a point of
	 coordinates (x, y) is mapped on a point of coordinates (u, v) such
	 that u = p0 + p1 x + p2 y + p3 x y and v = q0 + q1 x + q2 y + q3 x y.
	 Thus, u and v are both linear in x, and in y as well. The bilinear
	 transformation is determined by 8 parameters.
	 ********************************************************************/
	BILINEAR("Bilinear");
	private final String displayName;
	TransformationType(String displayName) {
		this.displayName = displayName;
	}
	public String getDisplayName() {
		return displayName;
	}
	/**
	 * @return all display names
	 */
	public static String [] getAllDisplayNames() {
		TransformationType [] values = TransformationType.values();
		String [] result = new String[values.length];
		for (int i=0; i<values.length; i++) {
			result[i] = values[i].getDisplayName();
		}
		return result;
	}
	/**
	 * Retrieve the transformation type with the given display name
	 * 
	 * @param displayName
	 * @return
	 */
	public static TransformationType fromDisplayName(String displayName) {
		for (final TransformationType value:TransformationType.values()) {
			if (value.getDisplayName().equals(displayName)) return value;
		}
		return null;
	}
}
