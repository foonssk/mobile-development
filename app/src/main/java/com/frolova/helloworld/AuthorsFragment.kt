package com.frolova.helloworld

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment

class AuthorsFragment : Fragment() {

    data class Author(val name: String, val photoRes: Int)

    private val authors = listOf(
        Author("Фролова Анастасия", R.drawable.frolova),
        Author("Зверев Михаил", R.drawable.misha),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_authors, container, false)
        val listView = view.findViewById<ListView>(R.id.listViewAuthors)

        listView.adapter = object : BaseAdapter() {
            override fun getCount() = authors.size
            override fun getItem(position: Int) = authors[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val itemView = convertView ?: inflater.inflate(R.layout.author_item, parent, false)
                val author = authors[position]
                val img = itemView.findViewById<ImageView>(R.id.imgAuthor)
                val txt = itemView.findViewById<TextView>(R.id.txtAuthor)
                img.setImageResource(author.photoRes)
                txt.text = author.name
                return itemView
            }
        }
        return view
    }
}