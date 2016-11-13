package agent;

import jade.util.leap.Serializable;

public class Artifact implements Serializable {
	
	private String id;
	private String name;
	private String creator;
	private String date;
	private String type;
	
	
	public Artifact(String id, String name, String creator, String date, String type) {		
		this.id = id;
		this.name = name;
		this.creator = creator;
		this.date = date;
		this.type = type;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCreator() {
		return creator;
	}
	public void setCreator(String creator) {
		this.creator = creator;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "id: " + id + ", Name: " + name + ", Creator: "+ creator + ", Date: "+ date + ", Type: " + type;
	}
	
	

}
