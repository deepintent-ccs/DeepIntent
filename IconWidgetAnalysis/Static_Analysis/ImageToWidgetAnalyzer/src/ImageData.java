

import java.util.HashSet;

public class ImageData {
	public String name;
	public String category;
	public String apk;
	public HashSet<String> methods = new HashSet<>();
	public HashSet<String> actions = new HashSet<>();
	public HashSet<String> uris = new HashSet<>();

	public ImageData(String apk, String drawable) {
		this.apk = apk;
		this.name = drawable;
	}

	@Override
	public String toString() {
		return "Image [name=" + name + ", category=" + category + ", apk=" + apk + ", methods=" + methods + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((apk == null) ? 0 : apk.hashCode());
		result = prime * result + ((category == null) ? 0 : category.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImageData other = (ImageData) obj;
		if (apk == null) {
			if (other.apk != null)
				return false;
		} else if (!apk.equals(other.apk))
			return false;
		if (category == null) {
			if (other.category != null)
				return false;
		} else if (!category.equals(other.category))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	
}