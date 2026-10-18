package com.darthademie.teleupm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class TarifasAdapter extends RecyclerView.Adapter<TarifasAdapter.ViewHolder> {

    private List<Tarifa> tarifasList = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Tarifa tarifa);
    }

    public TarifasAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setTarifas(List<Tarifa> tarifas) {
        this.tarifasList = tarifas;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tarifa, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tarifa tarifa = tarifasList.get(position);
        holder.tvNombre.setText(tarifa.nombre != null ? tarifa.nombre : "Sin nombre");
        holder.tvDesc.setText(tarifa.descripcion != null ? tarifa.descripcion : "Sin descripción");
        holder.itemView.setOnClickListener(v -> listener.onItemClick(tarifa));
    }

    @Override
    public int getItemCount() {
        return tarifasList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre;
        TextView tvDesc;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tv_tarifa_nombre);
            tvDesc = itemView.findViewById(R.id.tv_tarifa_desc);
        }
    }
}
