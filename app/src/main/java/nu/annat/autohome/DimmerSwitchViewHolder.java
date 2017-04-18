package nu.annat.autohome;

import android.util.Log;
import android.view.View;
import android.widget.SeekBar;

import nu.annat.autohome.api.DimmerSwitchUnit;
import nu.annat.autohome.api.SwitchUnit;
import nu.annat.autohome.databinding.DimmerSwitchRowBinding;
import nu.annat.autohome.rest.Server;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DimmerSwitchViewHolder extends BaseViewHolder<DimmerSwitchUnit, DimmerSwitchRowBinding> implements View.OnClickListener {

	public DimmerSwitchViewHolder(DimmerSwitchRowBinding binding) {
		super(binding);
		binding.setHandler(this);
		binding.ripple.setOnClickListener(this);
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		Log.d("test", ""+progress);
		Server.getInstance().getService().setDimmer(data.id, progress).enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

			}

			@Override
			public void onFailure(Call<ResponseBody> call, Throwable t) {

			}
		});
	}

	public void toggle() {
		SwitchUnit unit = data;
		unit.setOn(binding.ripple.isOn());
		Server.getInstance().getService().setOutlet(data.id, unit.isOn() ? "start" : "stop").enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

			}

			@Override
			public void onFailure(Call<ResponseBody> call, Throwable t) {
				//	binding.getData().to
			}
		});
	}

	@Override
	public void setData(DimmerSwitchUnit sensor) {
		super.setData(sensor);
		if (sensor == null) {
		} else {
			binding.setVariable(BR.data, sensor);
			binding.ripple.setState(sensor.isOn());
		}
		binding.executePendingBindings();
	}

	@Override
	public void onClick(View view) {
		toggle();
	}
}
