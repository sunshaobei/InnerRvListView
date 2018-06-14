package com.sunsh.myapplication

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*


import java.util.Random

class MainActivity : AppCompatActivity() {


    internal var color = intArrayOf(R.color.colorAccent, R.color.colorPrimary, R.color.colorPrimaryDark,Color.parseColor("#ff0000"),Color.parseColor("#ff00ff"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RvAdapter()
    }


    internal inner class RvAdapter : RecyclerView.Adapter<RvHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RvHolder {
            var itemView = LayoutInflater.from(this@MainActivity).inflate(R.layout.item_rv, null)
            var rvHolder = RvHolder(itemView);
            return rvHolder;
        }

        override fun onBindViewHolder(holder: RvHolder, position: Int) {
            holder.itemView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000)
            val listView = holder.itemView.findViewById<RvListView>(R.id.listView)
            listView.setBackgroundColor(color[Random().nextInt(5)])
            listView.setParent(recyclerView!!)
            listView.adapter = LvAdapter()
        }

        override fun getItemCount(): Int {
            return 10
        }
    }


    internal inner class LvAdapter : BaseAdapter() {
        override fun getCount(): Int {
            return 50
        }

        override fun getItem(position: Int): Any? {
            return null;
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var textView = TextView(this@MainActivity)
            textView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            textView.gravity = Gravity.CENTER
            textView.text = "item$position"
            return textView
        }


    }

    internal inner class RvHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

}
