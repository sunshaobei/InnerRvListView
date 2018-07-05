package com.sunsh.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.activity_main2.*

class Main2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        edit.setOnClickListener { onBackPressed() }
        recyclerView.layoutManager = LinearLayoutManager(this@Main2Activity)
        recyclerView.adapter = RvAdapter()
        over.defaultConfigAutoLoad()

    }

    internal inner class RvAdapter : RecyclerView.Adapter<RvHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RvHolder {
            var itemView = LayoutInflater.from(this@Main2Activity).inflate(R.layout.item_rv2, null)
            var rvHolder = RvHolder(itemView);
            return rvHolder;
        }

        override fun onBindViewHolder(holder: RvHolder, position: Int) {
            holder.itemView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000)
            holder.itemView.setBackgroundColor(Color.parseColor("#ff00ff"))
            holder.itemView.setOnClickListener { startActivity(Intent(this@Main2Activity, Main3Activity::class.java)) }
        }

        override fun getItemCount(): Int {
            return 10
        }
    }

    internal inner class RvHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
