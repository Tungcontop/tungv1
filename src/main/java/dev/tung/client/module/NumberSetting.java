package dev.tung.client.module;

/** A numeric slider setting shown under a module in the GUI. */
public final class NumberSetting extends Setting {
	public final double min;
	public final double max;
	public final double step;
	public final String suffix;
	private double value;

	public NumberSetting(String name, double min, double max, double step, double defaultValue, String suffix) {
		super(name);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
		set(defaultValue);
	}

	public double get() {
		return value;
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	public void set(double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) {
			clamped = min + Math.round((clamped - min) / step) * step;
			clamped = Math.max(min, Math.min(max, clamped));
		}
		this.value = clamped;
	}

	/** 0..1 position of the current value between min and max. */
	public float fraction() {
		return max == min ? 0f : (float) ((value - min) / (max - min));
	}

	public void setFraction(double f) {
		set(min + Math.max(0, Math.min(1, f)) * (max - min));
	}

	public String display() {
		String number = (step >= 1) ? Integer.toString(getInt()) : String.format("%.1f", value);
		return number + suffix;
	}
}
