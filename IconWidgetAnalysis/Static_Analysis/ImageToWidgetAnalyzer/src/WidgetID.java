

import java.util.Objects;

public class WidgetID {
	public long id;
	public String idName;
	public String layout;

	public WidgetID(long id, String idName, String layout) {
		this.id = id;
		this.idName = idName;
		this.layout = layout;
	}

	public WidgetID(long id, String idName) {
		this.id = id;
		this.idName = idName;
		this.layout = "gator";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		WidgetID widgetID = (WidgetID) o;
		return id == widgetID.id &&
				Objects.equals(idName, widgetID.idName) &&
				Objects.equals(layout, widgetID.layout);
	}

	@Override
	public int hashCode() {

		return Objects.hash(id, idName, layout);
	}

	@Override
	public String toString() {
		return "WidgetID{" +
				"id=" + id +
				", idName='" + idName + '\'' +
				", layout='" + layout + '\'' +
				'}';
	}
}
